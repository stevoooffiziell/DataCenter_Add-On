package de.f4ked.data_center_addon.hosting;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.catalog.PartPacks;
import com.example.datacenter.component.ComponentCondition;
import com.example.datacenter.component.FanSpec;
import com.example.datacenter.component.ProcessorKind;
import com.example.datacenter.component.ProcessorSpec;
import com.example.datacenter.component.PsuSpec;
import com.example.datacenter.component.RamSpec;
import com.example.datacenter.component.StorageSpec;
import com.example.datacenter.hosting.CustomerClass;
import com.example.datacenter.hosting.HostingReputation;
import com.example.datacenter.server.ServerItem;
import com.example.datacenter.server.ServerLayout;
import com.example.datacenter.wallet.OwnerMessages;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.network.CableType;
import de.f4ked.data_center_addon.network.NetworkContent;
import de.f4ked.data_center_addon.network.SwitchModel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Delivery orders (preview): clients that want goods delivered to a Hosting Manager before a deadline.
 * <ul>
 *   <li>Offers appear next to the hosting contracts (up to {@link #MAX_OFFERS}, one new per in-game day,
 *       each open for {@link #OFFER_DAYS} days). Size and pay grow with the unlocked customer class.</li>
 *   <li>Goods are assembled servers, computer parts of a kind, or this add-on's cables and switches
 *       that exist at the current date.</li>
 *   <li>"Deliver" takes matching items from the player's inventory; part deliveries count. When every
 *       goal is in, the client pays and the reputation rises. A missed deadline or a cancel costs
 *       reputation; what was delivered stays with the client.</li>
 * </ul>
 * Days are settled by {@link ContractService}'s daily tick.
 */
public final class DeliveryService {
    public static final int MAX_ACTIVE = 3;
    public static final int MAX_OFFERS = 3;
    static final int OFFER_DAYS = 3;

    public enum Action { ACCEPT, DECLINE, DELIVER, CANCEL }

    /** What a goal asks for. */
    public enum GoalKind {
        /** A specific item (cable, switch). */
        ITEM(0),
        /** An assembled server: mainboard and at least one CPU. */
        SERVER(900),
        /** An assembled server with at least one GPU. */
        GPU_SERVER(2_500),
        CPU(250),
        GPU(350),
        RAM(80),
        STORAGE(120),
        PSU(90),
        FAN(25);

        /** Pay per unit in dollars before the class multiplier (ITEM: from the item's price). */
        final int usd;

        GoalKind(int usd) {
            this.usd = usd;
        }
    }

    private DeliveryService() {
    }

    // ------------------------------------------------------------------ matching

    static boolean matches(ContractBook.Goal goal, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (goal.kind) {
            case ITEM -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(goal.item);
            case SERVER -> isServer(stack, false);
            case GPU_SERVER -> isServer(stack, true);
            default -> !ComponentCondition.isDefective(stack) && partMatches(goal.kind, PartPacks.spec(stack));
        };
    }

    private static boolean partMatches(GoalKind kind, Object spec) {
        return switch (kind) {
            case CPU -> spec instanceof ProcessorSpec p && p.getKind() == ProcessorKind.CPU;
            case GPU -> spec instanceof ProcessorSpec p && p.getKind() != ProcessorKind.CPU;
            case RAM -> spec instanceof RamSpec;
            case STORAGE -> spec instanceof StorageSpec;
            case PSU -> spec instanceof PsuSpec;
            case FAN -> spec instanceof FanSpec;
            default -> false;
        };
    }

    private static boolean isServer(ItemStack stack, boolean needGpu) {
        if (!(stack.getItem() instanceof ServerItem)) {
            return false;
        }
        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (!filled(contents, ServerLayout.SLOT_MAINBOARD) || !anyFilled(contents, ServerLayout.MAX_CPU, true)) {
            return false;
        }
        return !needGpu || anyFilled(contents, ServerLayout.MAX_GPU, false);
    }

    private static boolean anyFilled(ItemContainerContents contents, int slots, boolean cpu) {
        for (int i = 0; i < slots; i++) {
            if (filled(contents, cpu ? ServerLayout.cpuSlot(i) : ServerLayout.gpuSlot(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean filled(ItemContainerContents contents, int slot) {
        return slot < contents.getSlots() && !contents.getStackInSlot(slot).isEmpty();
    }

    // ------------------------------------------------------------------ days

    /** Ends one in-game day for the account's deliveries: fails overdue ones, refreshes the offers. */
    static void settleDay(ServerLevel level, UUID accountId, ContractBook.Account account, long day) {
        if (!Config.deliveriesEnabled()) {
            return;
        }
        MinecraftServer server = level.getServer();
        for (Iterator<ContractBook.Delivery> it = account.deliveries.iterator(); it.hasNext(); ) {
            ContractBook.Delivery d = it.next();
            if (day > d.lastDay) {
                it.remove();
                DataCenterApi.changeReputation(level, accountId, -d.reputation);
                OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.delivery.msg.failed",
                        clientName(d), d.reputation).withStyle(ChatFormatting.RED));
            }
        }
        account.deliveryOffers.removeIf(offer -> offer.lastDay < day);
        if (account.deliveryOffers.size() < MAX_OFFERS) {
            account.deliveryOffers.add(newOffer(level, accountId, account, LocalDate.ofEpochDay(day), level.getRandom()));
        }
    }

    /** The first offers, made once per account when one of its players opens a Hosting Manager. */
    static void seed(ServerLevel level, UUID accountId, ContractBook.Account account) {
        if (!Config.deliveriesEnabled() || account.deliveriesSeeded) {
            return;
        }
        LocalDate today = DataCenterApi.currentDate(level);
        while (account.deliveryOffers.size() < MAX_OFFERS) {
            account.deliveryOffers.add(newOffer(level, accountId, account, today, level.getRandom()));
        }
        account.deliveriesSeeded = true;
        ContractBook.get(level.getServer()).setDirty();
    }

    // ------------------------------------------------------------------ offers

    private static ContractBook.Delivery newOffer(ServerLevel level, UUID accountId, ContractBook.Account account,
                                                  LocalDate today, RandomSource random) {
        CustomerClass unlocked = HostingReputation.get(level, accountId).getUnlockedClass();
        int cls = Math.max(random.nextInt(unlocked.ordinal() + 1), random.nextInt(unlocked.ordinal() + 1));
        double scale = 1 + cls * 0.75;

        List<ContractBook.Goal> goals = new ArrayList<>();
        int wanted = random.nextFloat() < 0.35f ? 2 : 1;
        for (int attempt = 0; goals.size() < wanted && attempt < 10; attempt++) {
            ContractBook.Goal goal = newGoal(cls, scale, today, random);
            if (goal != null && goals.stream().noneMatch(g -> g.kind == goal.kind && g.item.equals(goal.item))) {
                goals.add(goal);
            }
        }
        if (goals.isEmpty()) {
            goals.add(new ContractBook.Goal(GoalKind.RAM, icon(GoalKind.RAM, random), 2));
        }

        double usd = 0;
        for (ContractBook.Goal goal : goals) {
            usd += goal.count * unitUsd(goal);
        }
        usd *= CustomerClass.values()[cls].incomeMultiplier * Config.contractPayMultiplier();
        long pay = Math.max(100, Math.round(usd) * 100);
        int days = 4 + cls * 2 + random.nextInt(3) + (goals.size() - 1) * 2;
        int reputation = 2 + cls * 3;
        int client = ContractService.pickClient(cls, null, random);
        return new ContractBook.Delivery(account.nextId++, client, goals, pay, reputation, days, today.toEpochDay() + OFFER_DAYS);
    }

    private static ContractBook.Goal newGoal(int cls, double scale, LocalDate today, RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 22) {
            return new ContractBook.Goal(GoalKind.SERVER, icon(GoalKind.SERVER, random), between(random, 1, 2, scale));
        }
        if (roll < 30 && cls >= 2) {
            return new ContractBook.Goal(GoalKind.GPU_SERVER, icon(GoalKind.GPU_SERVER, random), between(random, 1, 2, scale * 0.6));
        }
        if (roll < 62) {
            GoalKind[] parts = {GoalKind.CPU, GoalKind.GPU, GoalKind.RAM, GoalKind.RAM, GoalKind.STORAGE, GoalKind.PSU, GoalKind.FAN};
            GoalKind kind = parts[random.nextInt(parts.length)];
            int base = kind == GoalKind.FAN || kind == GoalKind.RAM ? 4 : 2;
            return new ContractBook.Goal(kind, icon(kind, random), between(random, base, base * 2, scale));
        }
        if (roll < 84) {
            List<CableType> cables = new ArrayList<>();
            for (CableType type : CableType.values()) {
                if (!type.releaseDate.isAfter(today)) {
                    cables.add(type);
                }
            }
            if (cables.isEmpty()) {
                return null;
            }
            // the newer cables of the time are asked for more often
            CableType type = cables.get(Math.max(random.nextInt(cables.size()), random.nextInt(cables.size())));
            int count = Math.round(between(random, 8, 24, scale) / 4f) * 4;
            return new ContractBook.Goal(GoalKind.ITEM, itemId(NetworkContent.CABLE_ITEMS.get(type).get()), Math.max(4, count));
        }
        List<SwitchModel> switches = new ArrayList<>();
        for (SwitchModel model : SwitchModel.values()) {
            if (!model.releaseDate.isAfter(today)) {
                switches.add(model);
            }
        }
        if (switches.isEmpty()) {
            return null;
        }
        SwitchModel model = switches.get(Math.max(random.nextInt(switches.size()), random.nextInt(switches.size())));
        return new ContractBook.Goal(GoalKind.ITEM, itemId(NetworkContent.SWITCH_ITEMS.get(model).get()), cls >= 3 ? 2 : 1);
    }

    private static int between(RandomSource random, int min, int max, double scale) {
        return Math.max(1, (int) Math.round((min + random.nextInt(max - min + 1)) * scale));
    }

    private static double unitUsd(ContractBook.Goal goal) {
        if (goal.kind != GoalKind.ITEM) {
            return goal.kind.usd;
        }
        for (CableType type : CableType.values()) {
            if (itemId(NetworkContent.CABLE_ITEMS.get(type).get()).equals(goal.item)) {
                return type.priceCents / 100.0 * 2.5;
            }
        }
        for (SwitchModel model : SwitchModel.values()) {
            if (itemId(NetworkContent.SWITCH_ITEMS.get(model).get()).equals(goal.item)) {
                return model.priceCents / 100.0 * 1.25;
            }
        }
        return 10;
    }

    /** An item to show for a goal kind: Data Center's server, or a random part of the kind. */
    private static ResourceLocation icon(GoalKind kind, RandomSource random) {
        Predicate<ItemStack> test = switch (kind) {
            case SERVER, GPU_SERVER -> stack -> stack.getItem() instanceof ServerItem;
            default -> stack -> partMatches(kind, PartPacks.spec(stack));
        };
        List<ResourceLocation> found = new ArrayList<>();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            if ("datacenter".equals(entry.getKey().location().getNamespace()) && test.test(new ItemStack(entry.getValue()))) {
                found.add(entry.getKey().location());
                if (kind == GoalKind.SERVER || kind == GoalKind.GPU_SERVER) {
                    break;
                }
            }
        }
        return found.isEmpty() ? ResourceLocation.withDefaultNamespace("chest") : found.get(random.nextInt(found.size()));
    }

    private static ResourceLocation itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    // ------------------------------------------------------------------ actions

    static void apply(ServerPlayer player, ServerLevel level, UUID accountId, int id, Action action) {
        if (!Config.deliveriesEnabled()) {
            return;
        }
        MinecraftServer server = level.getServer();
        ContractBook book = ContractBook.get(server);
        ContractBook.Account account = book.account(accountId);
        long today = DataCenterApi.currentDate(level).toEpochDay();
        switch (action) {
            case ACCEPT -> {
                ContractBook.Delivery offer = find(account.deliveryOffers, id);
                if (offer == null) {
                    return;
                }
                if (account.deliveries.size() >= MAX_ACTIVE) {
                    OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.delivery.msg.full", MAX_ACTIVE)
                            .withStyle(ChatFormatting.GOLD));
                    return;
                }
                account.deliveryOffers.remove(offer);
                offer.lastDay = today + offer.days;
                account.deliveries.add(offer);
                OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.delivery.msg.accepted",
                        clientName(offer), offer.days));
            }
            case DECLINE -> account.deliveryOffers.removeIf(d -> d.id == id);
            case DELIVER -> {
                ContractBook.Delivery d = find(account.deliveries, id);
                if (d == null) {
                    return;
                }
                int taken = take(player.getInventory(), d);
                if (taken == 0) {
                    player.displayClientMessage(Component.translatable("data_center_addon.delivery.msg.nothing")
                            .withStyle(ChatFormatting.GOLD), true);
                    return;
                }
                if (d.complete()) {
                    account.deliveries.remove(d);
                    DataCenterApi.deposit(level, accountId, d.payCents);
                    DataCenterApi.changeReputation(level, accountId, d.reputation);
                    OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.delivery.msg.completed",
                            clientName(d), ContractService.usd(d.payCents), d.reputation).withStyle(ChatFormatting.GREEN));
                } else {
                    player.displayClientMessage(Component.translatable("data_center_addon.delivery.msg.delivered", taken), true);
                }
            }
            case CANCEL -> {
                ContractBook.Delivery d = find(account.deliveries, id);
                if (d == null) {
                    return;
                }
                account.deliveries.remove(d);
                DataCenterApi.changeReputation(level, accountId, -d.reputation);
                OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.delivery.msg.cancelled",
                        clientName(d), d.reputation).withStyle(ChatFormatting.RED));
            }
        }
        book.setDirty();
    }

    /** Takes what the delivery still needs from the inventory; returns the number of items taken. */
    private static int take(Inventory inventory, ContractBook.Delivery d) {
        int taken = 0;
        for (ContractBook.Goal goal : d.goals) {
            for (int slot = 0; slot < inventory.getContainerSize() && !goal.done(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (matches(goal, stack)) {
                    int n = Math.min(stack.getCount(), goal.count - goal.delivered);
                    stack.shrink(n);
                    goal.delivered += n;
                    taken += n;
                }
            }
        }
        if (taken > 0) {
            inventory.setChanged();
        }
        return taken;
    }

    private static ContractBook.Delivery find(List<ContractBook.Delivery> list, int id) {
        for (ContractBook.Delivery d : list) {
            if (d.id == id) {
                return d;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ snapshot

    /** The account's running deliveries and offers as the manager shows them; {@code inventory} counts what the player could deliver. */
    static List<HostingSnapshot.Delivery> snapshot(ServerLevel level, UUID accountId, ContractBook.Account account,
                                                   Inventory inventory) {
        if (!Config.deliveriesEnabled()) {
            return List.of();
        }
        seed(level, accountId, account);
        long today = DataCenterApi.currentDate(level).toEpochDay();
        List<HostingSnapshot.Delivery> list = new ArrayList<>();
        for (ContractBook.Delivery d : account.deliveries) {
            list.add(toSnapshot(d, false, (int) Math.max(0, d.lastDay - today + 1), inventory));
        }
        for (ContractBook.Delivery d : account.deliveryOffers) {
            list.add(toSnapshot(d, true, (int) Math.max(0, d.lastDay - today + 1), inventory));
        }
        return List.copyOf(list);
    }

    private static HostingSnapshot.Delivery toSnapshot(ContractBook.Delivery d, boolean offer, int daysLeft, Inventory inventory) {
        List<HostingSnapshot.Goal> goals = new ArrayList<>();
        for (ContractBook.Goal g : d.goals) {
            int inBag = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (matches(g, stack)) {
                    inBag += stack.getCount();
                }
            }
            goals.add(new HostingSnapshot.Goal(g.kind.ordinal(), g.item, g.count, g.delivered, inBag));
        }
        return new HostingSnapshot.Delivery(d.id, offer, d.client, List.copyOf(goals), d.payCents, d.reputation, d.days, daysLeft);
    }

    private static Component clientName(ContractBook.Delivery d) {
        return Component.translatable("data_center_addon.contract.client." + d.client);
    }
}
