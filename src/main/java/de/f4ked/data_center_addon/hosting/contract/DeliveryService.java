package de.f4ked.data_center_addon.hosting.contract;

import de.f4ked.data_center_addon.industry.Product;
import de.f4ked.data_center_addon.hosting.reputation.ClassRules;
import de.f4ked.data_center_addon.hosting.reputation.CustomerTier;
import de.f4ked.data_center_addon.hosting.reputation.Renown;
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
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import de.f4ked.data_center_addon.hosting.Money;
import de.f4ked.data_center_addon.hosting.startup.Milestone;
import de.f4ked.data_center_addon.hosting.startup.Milestones;
import de.f4ked.data_center_addon.hosting.startup.StartupService;
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
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Delivery orders (preview): clients that want goods delivered to a Hosting Manager before a deadline.
 * <ul>
 *   <li>Offers appear next to the hosting contracts (up to {@link #maxOffers}, one new per in-game day,
 *       each open for {@link #OFFER_DAYS} days). Size and pay grow with the unlocked customer class.</li>
 *   <li>Every offer has a {@link ContractRarity}, like the hosting contracts: a rarer order wants more goods
 *       and gives more time, but pays much more and brings more reputation (and costs more when it fails).
 *       The better the customer class, the more often rare ones come, and the more orders can run at once
 *       ({@link #maxActive}).</li>
 *   <li>Goods are assembled servers, computer parts of a kind, defective parts (scrap), or this
 *       add-on's cables and switches that exist at the current date.</li>
 *   <li>A young company ({@link StartupService#earlyPhase}) gets simpler orders: one goal of cables or
 *       small parts, more time, more offers.</li>
 *   <li>"Deliver" takes matching items from the player's inventory; part deliveries count. When every
 *       goal is in, the client pays and the reputation rises. A missed deadline or a cancel costs
 *       reputation; what was delivered stays with the client.</li>
 * </ul>
 * Days are settled by {@link ContractService}'s daily tick.
 */
public final class DeliveryService {
    /** Offers a young company can have at least; it also gets two new offers a day. */
    public static final int MAX_OFFERS_EARLY = 4;
    static final int OFFER_DAYS = 3;
    /** Pay for cables and switches as a multiple of their price (they are cheap to make). */
    private static final double CABLE_PAY = 2.5;
    private static final double SWITCH_PAY = 1.25;
    /** Delivery orders pay this times what the Sales Terminal pays at full demand for a component. */
    private static final double COMPONENT_PAY = 1.6;
    /** The factory components clients order. */
    private static final Product[] COMPONENT_GOALS = {Product.CIRCUIT_BOARD, Product.CAPACITOR, Product.CONNECTOR,
            Product.MICROCHIP, Product.COPPER_WIRE, Product.PLASTIC_PARTS};

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
        FAN(25),
        /** Any defective computer part: bought as scrap. */
        SCRAP(15);

        /** Pay per unit in dollars before the class multiplier (ITEM: from the item's price). */
        final int usd;

        GoalKind(int usd) {
            this.usd = usd;
        }
    }

    private DeliveryService() {
    }

    /** Orders that can run at once for an account of this customer class: as many as hosting contracts. */
    public static int maxActive(int customerTier) {
        return ContractService.maxActive(customerTier);
    }

    /** Offers that wait at once for an account of this customer class; a young company gets at least four. */
    public static int maxOffers(int customerTier, boolean early) {
        int max = ContractService.maxOffers(customerTier);
        return early ? Math.max(MAX_OFFERS_EARLY, max) : max;
    }

    private static int tierOf(ServerLevel level, UUID accountId) {
        return ClassRules.tier(level.getServer(), accountId).ordinal();
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
            case SCRAP -> ComponentCondition.isDefective(stack) && isPart(PartPacks.spec(stack));
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

    private static boolean isPart(Object spec) {
        return spec instanceof ProcessorSpec || spec instanceof RamSpec || spec instanceof StorageSpec || spec instanceof PsuSpec
                || spec instanceof FanSpec;
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
                Renown.add(level, accountId, -d.reputation, Renown.Source.PENALTIES);
                OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.delivery.msg.failed",
                        clientName(d), d.reputation).withStyle(ChatFormatting.RED));
            }
        }
        account.deliveryOffers.removeIf(offer -> offer.lastDay < day);
        boolean early = StartupService.earlyPhase(level, accountId);
        int max = maxOffers(tierOf(level, accountId), early);
        for (int i = 0; i < (early ? 2 : 1) && account.deliveryOffers.size() < max; i++) {
            account.deliveryOffers.add(newOffer(level, accountId, account, LocalDate.ofEpochDay(day), level.getRandom()));
        }
    }

    /** The first offers, made once per account when one of its players opens a Hosting Manager. */
    static void seed(ServerLevel level, UUID accountId, ContractBook.Account account) {
        if (!Config.deliveriesEnabled() || account.deliveriesSeeded) {
            return;
        }
        LocalDate today = DataCenterApi.currentDate(level);
        int max = maxOffers(tierOf(level, accountId), StartupService.earlyPhase(level, accountId));
        while (account.deliveryOffers.size() < max) {
            account.deliveryOffers.add(newOffer(level, accountId, account, today, level.getRandom()));
        }
        account.deliveriesSeeded = true;
        ContractBook.get(level.getServer()).setDirty();
    }

    // ------------------------------------------------------------------ offers

    private static ContractBook.Delivery newOffer(ServerLevel level, UUID accountId, ContractBook.Account account,
                                                  LocalDate today, RandomSource random) {
        CustomerTier unlocked = ClassRules.tier(level.getServer(), accountId);
        int t = Math.max(random.nextInt(unlocked.ordinal() + 1), random.nextInt(unlocked.ordinal() + 1));
        CustomerTier tier = CustomerTier.of(t);
        // the goals and the client follow Data Center's class the add-on's class belongs to
        int cls = tier.base.ordinal();
        boolean early = StartupService.earlyPhase(level, accountId);
        ContractRarity rarity = ContractRarity.roll(unlocked, random);
        double scale = (1 + t * 0.4) * rarity.size;

        List<ContractBook.Goal> goals = new ArrayList<>();
        int wanted = !early && random.nextFloat() < 0.35f ? 2 : 1;
        for (int attempt = 0; goals.size() < wanted && attempt < 10; attempt++) {
            ContractBook.Goal goal = early ? newEarlyGoal(today, random) : newGoal(cls, scale, today, random);
            if (goal != null && goals.stream().noneMatch(g -> g.kind == goal.kind && g.item.equals(goal.item))) {
                goals.add(goal);
            }
        }
        if (goals.isEmpty()) {
            goals.add(new ContractBook.Goal(GoalKind.RAM, icon(GoalKind.RAM, random), 2));
        }

        double usd = 0;
        for (ContractBook.Goal goal : goals) {
            usd += goal.count * unitUsd(goal, today);
        }
        usd *= tier.income * rarity.pay * Config.contractPayMultiplier();
        long pay = Math.max(100, Math.round(usd) * 100);
        // more goods want more time: +1 day for Uncommon and Rare, +2 for Epic, +3 for Legendary
        int days = 4 + t + random.nextInt(3) + (goals.size() - 1) * 2 + (early ? 2 : 0)
                + (int) Math.round((rarity.size - 1) * 4);
        int reputation = (int) Math.round((2 + t * 1.5) * rarity.reputation * Config.orderRenown());
        int client = ContractService.pickClient(cls, null, random);
        ContractBook.Delivery delivery = new ContractBook.Delivery(account.nextId++, client, goals, pay, reputation, days,
                today.toEpochDay() + OFFER_DAYS - 1);
        delivery.rarity = rarity;
        return delivery;
    }

    private static ContractBook.Goal newGoal(int cls, double scale, LocalDate today, RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 22) {
            return new ContractBook.Goal(GoalKind.SERVER, icon(GoalKind.SERVER, random), between(random, 1, 2, scale));
        }
        if (roll < 30 && cls >= 2) {
            return new ContractBook.Goal(GoalKind.GPU_SERVER, icon(GoalKind.GPU_SERVER, random), between(random, 1, 2, scale * 0.6));
        }
        if (roll < 56) {
            GoalKind[] parts = {GoalKind.CPU, GoalKind.GPU, GoalKind.RAM, GoalKind.RAM, GoalKind.STORAGE, GoalKind.PSU, GoalKind.FAN};
            GoalKind kind = parts[random.nextInt(parts.length)];
            int base = kind == GoalKind.FAN || kind == GoalKind.RAM ? 4 : 2;
            return new ContractBook.Goal(kind, icon(kind, random), between(random, base, base * 2, scale));
        }
        if (roll < 64) {
            return new ContractBook.Goal(GoalKind.SCRAP, icon(GoalKind.SCRAP, random), between(random, 2, 6, scale));
        }
        if (roll < 74) {
            return componentGoal(scale, random);
        }
        if (roll < 88) {
            List<CableType> cables = releasedCables(today);
            if (cables.isEmpty()) {
                return null;
            }
            int count = Math.round(between(random, 8, 24, scale) / 4f) * 4;
            return new ContractBook.Goal(GoalKind.ITEM, cableId(newerOf(cables, random)), Math.max(4, count));
        }
        List<SwitchModel> switches = releasedSwitches(today);
        if (switches.isEmpty()) {
            return null;
        }
        return new ContractBook.Goal(GoalKind.ITEM, switchId(newerOf(switches, random)), cls >= 3 ? 2 : 1);
    }

    /** Factory components ({@link Product}): the cheaper the component, the more the client wants. */
    private static ContractBook.Goal componentGoal(double scale, RandomSource random) {
        Product product = COMPONENT_GOALS[random.nextInt(COMPONENT_GOALS.length)];
        int count = switch (product) {
            case MICROCHIP -> between(random, 4, 12, scale);
            case CIRCUIT_BOARD, CONNECTOR -> between(random, 8, 24, scale);
            default -> between(random, 16, 48, scale);
        };
        return new ContractBook.Goal(GoalKind.ITEM, itemId(product.item()), count);
    }

    /** A goal for a young company: cables of the time or a few small parts, things it can make or buy cheaply. */
    private static ContractBook.Goal newEarlyGoal(LocalDate today, RandomSource random) {
        List<CableType> cables = releasedCables(today);
        if (random.nextFloat() < 0.6f && !cables.isEmpty()) {
            CableType type = cables.get(random.nextInt(cables.size()));
            return new ContractBook.Goal(GoalKind.ITEM, cableId(type), 4 + 4 * random.nextInt(3));
        }
        GoalKind[] small = {GoalKind.RAM, GoalKind.FAN, GoalKind.STORAGE, GoalKind.SCRAP};
        GoalKind kind = small[random.nextInt(small.length)];
        return new ContractBook.Goal(kind, icon(kind, random), 1 + random.nextInt(3));
    }

    /** The add-on's cables on sale at {@code today}, oldest first. */
    private static List<CableType> releasedCables(LocalDate today) {
        List<CableType> cables = new ArrayList<>();
        for (CableType type : CableType.values()) {
            if (!type.releaseDate.isAfter(today)) {
                cables.add(type);
            }
        }
        return cables;
    }

    /** The add-on's switches on sale at {@code today}, oldest first. */
    private static List<SwitchModel> releasedSwitches(LocalDate today) {
        List<SwitchModel> switches = new ArrayList<>();
        for (SwitchModel model : SwitchModel.values()) {
            if (!model.releaseDate.isAfter(today)) {
                switches.add(model);
            }
        }
        return switches;
    }

    /** One of a non-empty list ordered oldest first; the newer models of the time come up more often. */
    private static <T> T newerOf(List<T> list, RandomSource random) {
        return list.get(Math.max(random.nextInt(list.size()), random.nextInt(list.size())));
    }

    private static ResourceLocation cableId(CableType type) {
        return itemId(NetworkContent.CABLE_ITEMS.get(type).get());
    }

    private static ResourceLocation switchId(SwitchModel model) {
        return itemId(NetworkContent.SWITCH_ITEMS.get(model).get());
    }

    private static int between(RandomSource random, int min, int max, double scale) {
        return Math.max(1, (int) Math.round((min + random.nextInt(max - min + 1)) * scale));
    }

    private static double unitUsd(ContractBook.Goal goal, LocalDate today) {
        if (goal.kind != GoalKind.ITEM) {
            return goal.kind.usd;
        }
        for (Product product : COMPONENT_GOALS) {
            if (itemId(product.item()).equals(goal.item)) {
                return product.baseCents(today) / 100.0 * COMPONENT_PAY;
            }
        }
        for (CableType type : CableType.values()) {
            if (cableId(type).equals(goal.item)) {
                return type.priceCents / 100.0 * CABLE_PAY;
            }
        }
        for (SwitchModel model : SwitchModel.values()) {
            if (switchId(model).equals(goal.item)) {
                return model.priceCents / 100.0 * SWITCH_PAY;
            }
        }
        return 10;
    }

    /** An item to show for a goal kind: Data Center's server, or a random part of the kind. */
    private static ResourceLocation icon(GoalKind kind, RandomSource random) {
        Predicate<ItemStack> test = switch (kind) {
            case SERVER, GPU_SERVER -> stack -> stack.getItem() instanceof ServerItem;
            case SCRAP -> stack -> isPart(PartPacks.spec(stack));
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

    public static void apply(ServerPlayer player, ServerLevel level, UUID accountId, int id, Action action) {
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
                int maxActive = maxActive(tierOf(level, accountId));
                if (account.deliveries.size() >= maxActive) {
                    OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.delivery.msg.full", maxActive)
                            .withStyle(ChatFormatting.GOLD));
                    return;
                }
                account.deliveryOffers.remove(offer);
                offer.lastDay = today + offer.days - 1;
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
                    Renown.add(level, accountId, d.reputation, Renown.Source.ORDERS);
                    OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.delivery.msg.completed",
                            clientName(d), Money.usd(d.payCents), d.reputation).withStyle(ChatFormatting.GREEN));
                    Milestones.award(level, accountId, Milestone.FIRST_DELIVERY);
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
                Renown.add(level, accountId, -d.reputation, Renown.Source.PENALTIES);
                OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.delivery.msg.cancelled",
                        clientName(d), d.reputation).withStyle(ChatFormatting.RED));
            }
        }
        book.setDirty();
    }

    /** Takes what the delivery still needs from the inventory; returns the number of items taken. */
    private static int take(Inventory inventory, ContractBook.Delivery d) {
        int taken = 0;
        // a GPU server also counts as a server: fill the GPU server goal first so it does not run dry
        List<ContractBook.Goal> goals = new ArrayList<>(d.goals);
        goals.sort(Comparator.comparingInt(g -> g.kind == GoalKind.GPU_SERVER ? 0 : 1));
        for (ContractBook.Goal goal : goals) {
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
    public static List<HostingSnapshot.Delivery> snapshot(ServerLevel level, UUID accountId, ContractBook.Account account,
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
        return new HostingSnapshot.Delivery(d.id, offer, d.client, List.copyOf(goals), d.payCents, d.reputation, d.days, daysLeft,
                d.rarity.ordinal());
    }

    private static Component clientName(ContractBook.Delivery d) {
        return ContractService.clientName(d.client);
    }
}
