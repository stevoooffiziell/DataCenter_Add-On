package de.f4ked.data_center_addon.hosting.contract;

import de.f4ked.data_center_addon.hosting.reputation.ClassRules;
import de.f4ked.data_center_addon.hosting.reputation.CustomerTier;
import de.f4ked.data_center_addon.hosting.reputation.Renown;
import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.hosting.CustomerClass;
import com.example.datacenter.hosting.HostingCustomers;
import com.example.datacenter.hosting.HostingModeRates;
import com.example.datacenter.hosting.HostingProduct;
import com.example.datacenter.hosting.HostingReputation;
import com.example.datacenter.wallet.OwnerMessages;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import de.f4ked.data_center_addon.hosting.Money;
import de.f4ked.data_center_addon.hosting.startup.Milestone;
import de.f4ked.data_center_addon.hosting.startup.Milestones;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hosting contracts: clients that want a number of places of one product kept free for them for a
 * number of in-game days.
 * <ul>
 *   <li>Offers appear in the Hosting Manager (up to {@link #maxOffers}, one new per day, each open for
 *       {@link #OFFER_DAYS} days). Every offer has a {@link ContractRarity} (grey Common to orange
 *       Legendary); the better the customer class, the more often rare ones come. Their size, length and pay grow with the customer class the
 *       account's reputation has unlocked. Clients only ask for products the account has free places
 *       of, never for more places than are free; with no free places no offers come, and an offer can
 *       only be accepted while its places are free.</li>
 *   <li>A running contract is checked once a second: are enough places of its product free (capacity
 *       of the account's racks minus their customers, the same figures Data Center's market uses)?
 *       A day counts if they were free {@link #DAY_SHARE} of the time; it is paid. A missed day is a
 *       strike; {@link #MAX_STRIKES} strikes end the contract with a penalty.</li>
 *   <li>How many contracts can run at once ({@link #maxActive}) and how many offers wait
 *       ({@link #maxOffers}) grows with the customer class the reputation has unlocked.</li>
 *   <li>A contract served to the end pays a bonus and raises the reputation; one that fails or is
 *       cancelled costs the penalty and reputation.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Data_center_addon.MODID)
public final class ContractService {
    /** Contracts that can run at once with the first customer class; one more every {@link #CLASSES_PER_SLOT} classes. */
    public static final int BASE_ACTIVE = 3;
    public static final int CLASSES_PER_SLOT = 3;
    /** Offers waiting with the first customer class; one more every {@link #CLASSES_PER_OFFER} classes. */
    public static final int BASE_OFFERS = 3;
    static final int CLASSES_PER_OFFER = 4;
    public static final int MAX_STRIKES = 3;
    static final int OFFER_DAYS = 4;
    /** Share of a day the places must be free for the day to count, in percent. */
    public static final int DAY_SHARE_PERCENT = 80;
    static final double DAY_SHARE = DAY_SHARE_PERCENT / 100.0;
    private static final int SAMPLE_TICKS = 20;
    /** Most in-game days settled at once, e.g. after sleeping through the night. */
    private static final int MAX_CATCH_UP_DAYS = 60;
    /** Share of the product's list price per account and month a contract pays. */
    private static final double PAY_SHARE = 0.4;

    /** Client names (lang keys contract.client.N) and the customer class each belongs to. */
    private static final int[] CLIENT_CLASS = {0, 0, 0, 1, 1, 1, 2, 2, 3, 3, 4, 4};
    private static final int GAMING_CLAN = 2;
    private static final int[] SIZE_MIN = {5, 10, 25, 50, 100};
    private static final int[] SIZE_MAX = {15, 30, 60, 120, 250};
    private static final int[] DAYS_MIN = {10, 14, 20, 25, 30};
    private static final int[] DAYS_MAX = {20, 28, 35, 45, 60};
    /** Share of the offers that are emergency orders (short, well paid, open for one day). */
    private static final float URGENT_CHANCE = 0.15f;
    private static final double URGENT_PAY = 2.5;
    /** Share of a hobbyist account's offers that are mini orders (a few places, no risk). */
    private static final float MINI_CHANCE = 0.4f;

    public enum Action { ACCEPT, CANCEL }

    private ContractService() {
    }

    /** Contracts that can run at once for an account of this customer class ({@code CustomerTier} ordinal). */
    public static int maxActive(int customerTier) {
        return BASE_ACTIVE + Math.max(0, customerTier) / CLASSES_PER_SLOT;
    }

    /** Offers that wait at once for an account of this customer class ({@code CustomerTier} ordinal). */
    public static int maxOffers(int customerTier) {
        return BASE_OFFERS + Math.max(0, customerTier) / CLASSES_PER_OFFER;
    }

    private static int maxActive(MinecraftServer server, UUID accountId) {
        return maxActive(ClassRules.tier(server, accountId).ordinal());
    }

    private static int maxOffers(MinecraftServer server, UUID accountId) {
        return maxOffers(ClassRules.tier(server, accountId).ordinal());
    }

    /** Places per account a contract of this product asks for, relative to shared web hosting. */
    private static double sizeScale(HostingProduct product) {
        return switch (product) {
            case SHARED_WEB -> 1.0;
            case SHARED_MAIL -> 2.0;
            case SHARED_RESELLER -> 0.5;
            case SHARED_GAME -> 0.25;
            case VPS -> 0.3;
            case COLOCATION -> 0.08;
        };
    }

    // ------------------------------------------------------------------ figures

    /** Places of a product the account's racks offer beyond their customers. */
    public static double freePlaces(ServerLevel level, UUID account, HostingProduct product) {
        HostingCustomers customers = HostingCustomers.get(level);
        return Math.max(0, customers.placesTotal(account, product) - customers.total(account, product));
    }

    /** Places the account's running contracts ask for, by product. */
    static Map<HostingProduct, Integer> required(ContractBook.Account account) {
        Map<HostingProduct, Integer> required = new EnumMap<>(HostingProduct.class);
        for (ContractBook.Contract c : account.active) {
            required.merge(c.product, c.accounts, Integer::sum);
        }
        return required;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!(Config.contractsEnabled() || Config.deliveriesEnabled()) || server.getTickCount() % SAMPLE_TICKS != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        long today = DataCenterApi.currentDate(level).toEpochDay();
        ContractBook book = ContractBook.get(server);
        boolean dirty = false;
        for (Map.Entry<UUID, ContractBook.Account> entry : book.accounts().entrySet()) {
            UUID accountId = entry.getKey();
            ContractBook.Account account = entry.getValue();
            if (!account.active.isEmpty()) {
                Map<HostingProduct, Integer> required = required(account);
                Map<HostingProduct, Boolean> met = new EnumMap<>(HostingProduct.class);
                for (Map.Entry<HostingProduct, Integer> need : required.entrySet()) {
                    met.put(need.getKey(), freePlaces(level, accountId, need.getKey()) >= need.getValue());
                }
                for (ContractBook.Contract c : account.active) {
                    c.samples++;
                    if (met.get(c.product)) {
                        c.samplesMet++;
                    }
                }
                dirty = true;
            }
            if (account.lastDay < 0) {
                account.lastDay = today;
                dirty = true;
            } else if (today > account.lastDay) {
                int days = (int) Math.min(MAX_CATCH_UP_DAYS, today - account.lastDay);
                for (int i = 0; i < days; i++) {
                    long day = account.lastDay + i + 1;
                    if (Config.contractsEnabled()) {
                        settleDay(level, accountId, account, day);
                    }
                    DeliveryService.settleDay(level, accountId, account, day);
                }
                account.lastDay = today;
                dirty = true;
            }
        }
        if (dirty) {
            book.setDirty();
        }
    }

    /** Ends one in-game day: pays or strikes every running contract, refreshes the offers. */
    private static void settleDay(ServerLevel level, UUID accountId, ContractBook.Account account, long day) {
        MinecraftServer server = level.getServer();
        for (Iterator<ContractBook.Contract> it = account.active.iterator(); it.hasNext(); ) {
            ContractBook.Contract c = it.next();
            // no samples: the day was skipped (sleeping); it ends like the last day that was measured
            boolean met = c.samples == 0 ? c.lastMet : c.samplesMet >= DAY_SHARE * c.samples;
            c.lastMet = met;
            if (met) {
                DataCenterApi.deposit(level, accountId, c.centsPerDay);
                c.daysDone++;
            } else {
                c.strikes++;
                OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.contract.msg.strike",
                        clientName(c), Component.translatable(c.product.translationKey()), c.strikes, MAX_STRIKES)
                        .withStyle(ChatFormatting.GOLD));
            }
            c.samples = 0;
            c.samplesMet = 0;
            if (c.strikes >= MAX_STRIKES) {
                it.remove();
                charge(level, accountId, c);
                OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.contract.msg.failed",
                        clientName(c), Money.usd(c.penaltyCents), c.reputationLoss()).withStyle(ChatFormatting.RED));
            } else if (c.daysDone >= c.days) {
                it.remove();
                DataCenterApi.deposit(level, accountId, c.bonusCents);
                Renown.add(level, accountId, c.reputation, Renown.Source.ORDERS);
                OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.contract.msg.completed",
                        clientName(c), Money.usd(c.bonusCents), c.reputation).withStyle(ChatFormatting.GREEN));
                Milestones.award(level, accountId, Milestone.FIRST_CONTRACT);
            }
        }
        account.offers.removeIf(offer -> offer.expiresDay < day);
        if (account.offers.size() < maxOffers(server, accountId)) {
            ContractBook.Contract offer = newOffer(level, accountId, account, LocalDate.ofEpochDay(day), level.getRandom());
            if (offer != null) {
                account.offers.add(offer);
            }
        }
    }

    /** Takes the penalty (as much of it as the wallet holds) and the reputation for a failed or cancelled contract. */
    private static void charge(ServerLevel level, UUID accountId, ContractBook.Contract c) {
        long take = Math.min(c.penaltyCents, Math.max(0, DataCenterApi.balanceCents(level, accountId)));
        DataCenterApi.withdraw(level, accountId, take);
        if (c.reputationLoss() > 0) {
            Renown.add(level, accountId, -c.reputationLoss(), Renown.Source.PENALTIES);
        }
    }

    // ------------------------------------------------------------------ offers

    /** Makes sure the account has its offers; called when one of its players opens a Hosting Manager. */
    static ContractBook.Account account(ServerLevel level, UUID accountId) {
        ContractBook book = ContractBook.get(level.getServer());
        ContractBook.Account account = book.account(accountId);
        if (Config.contractsEnabled() && account.offers.isEmpty() && account.active.isEmpty() && account.lastDay < 0) {
            LocalDate today = DataCenterApi.currentDate(level);
            for (int i = 0, n = maxOffers(level.getServer(), accountId); i < n; i++) {
                ContractBook.Contract offer = newOffer(level.getServer().overworld(), accountId, account, today, level.getRandom());
                if (offer != null) {
                    account.offers.add(offer);
                }
            }
            account.lastDay = today.toEpochDay();
            book.setDirty();
        }
        return account;
    }

    /**
     * A new offer, or null when the account has no free places of any product: clients only ask for
     * places the account offers and has free (beyond its running contracts), and never for more.
     */
    @org.jetbrains.annotations.Nullable
    private static ContractBook.Contract newOffer(ServerLevel level, UUID accountId, ContractBook.Account account,
                                                  LocalDate today, RandomSource random) {
        CustomerTier unlocked = ClassRules.tier(level.getServer(), accountId);
        // two rolls, the better one: most offers come from the best class unlocked
        int t = Math.max(random.nextInt(unlocked.ordinal() + 1), random.nextInt(unlocked.ordinal() + 1));
        CustomerTier tier = CustomerTier.of(t);
        // size, duration and client follow Data Center's class the add-on's class belongs to
        int cls = tier.base.ordinal();

        // only products with free places, the more free places the more often
        List<HostingProduct> products = new ArrayList<>();
        Map<HostingProduct, Integer> required = required(account);
        Map<HostingProduct, Integer> spare = new EnumMap<>(HostingProduct.class);
        for (HostingProduct p : HostingProduct.values()) {
            if (p.mode().existsOn(today) && p.offer().existsOn(today)) {
                int free = (int) Math.floor(freePlaces(level, accountId, p) - required.getOrDefault(p, 0));
                if (free >= 1) {
                    spare.put(p, free);
                    products.add(p);
                }
            }
        }
        if (products.isEmpty()) {
            return null;
        }
        HostingProduct product = products.get(random.nextInt(products.size()));
        int free = spare.get(product);

        // hobbyist accounts often get mini orders; any account now and then an emergency order
        boolean mini = unlocked.base == CustomerClass.HOBBYISTS && random.nextFloat() < MINI_CHANCE;
        boolean urgent = !mini && random.nextFloat() < URGENT_CHANCE;
        // mini orders are always common; every other offer rolls its rarity
        ContractRarity rarity = mini ? ContractRarity.COMMON : ContractRarity.roll(unlocked, random);

        double size = SIZE_MIN[cls] + random.nextDouble() * (SIZE_MAX[cls] - SIZE_MIN[cls]);
        int accounts = nice(Math.max(1, (int) Math.round(size * rarity.size * sizeScale(product))));
        int days = DAYS_MIN[cls] + random.nextInt(DAYS_MAX[cls] - DAYS_MIN[cls] + 1);
        if (mini) {
            accounts = 1 + random.nextInt(sizeScale(product) >= 0.5 ? 5 : 2);
            days = 3 + random.nextInt(4);
        } else if (urgent) {
            days = 2 + random.nextInt(3);
        }
        accounts = Math.max(1, Math.min(accounts, free));
        double monthlyUsd = accounts * HostingModeRates.unitUsd(product, today) * PAY_SHARE
                * tier.income * rarity.pay * Config.contractPayMultiplier() * (urgent ? URGENT_PAY : 1);
        long perDay = Math.max(25, Math.round(monthlyUsd / 30.0 * 100));
        long bonus = perDay * days / 2;
        // the pay already grows with the rarity; the penalty grows by the rarity's own (smaller) factor
        long penalty = mini ? 0 : Math.round(perDay * (urgent ? 3 : 5) * rarity.penalty / rarity.pay);
        int reputation = (int) Math.round((mini ? 1 : (3 + t * 2 + (urgent ? 2 : 0)) * rarity.reputation) * Config.orderRenown());

        int client = pickClient(cls, product, random);
        ContractBook.Contract contract = new ContractBook.Contract(account.nextId++, client, product, accounts, days, perDay, bonus,
                penalty, reputation, today.toEpochDay() + (urgent ? 0 : OFFER_DAYS - 1));
        contract.urgent = urgent;
        contract.mini = mini;
        contract.rarity = rarity;
        return contract;
    }

    static int pickClient(int cls, HostingProduct product, RandomSource random) {
        if (product == HostingProduct.SHARED_GAME && cls == 0) {
            return GAMING_CLAN;
        }
        List<Integer> matching = new ArrayList<>();
        for (int i = 0; i < CLIENT_CLASS.length; i++) {
            if (CLIENT_CLASS[i] == cls) {
                matching.add(i);
            }
        }
        return matching.get(random.nextInt(matching.size()));
    }

    /** 1..20 as they are, then to 5s, then to 10s. */
    private static int nice(int n) {
        if (n <= 20) {
            return n;
        }
        int step = n <= 100 ? 5 : 10;
        return Math.round((float) n / step) * step;
    }

    // ------------------------------------------------------------------ actions

    /** Accepts an offer or cancels a running contract for the player's account. */
    public static void apply(ServerLevel level, UUID accountId, int contractId, Action action) {
        if (!Config.contractsEnabled()) {
            return;
        }
        MinecraftServer server = level.getServer();
        ContractBook book = ContractBook.get(server);
        ContractBook.Account account = book.account(accountId);
        switch (action) {
            case ACCEPT -> {
                ContractBook.Contract offer = find(account.offers, contractId);
                if (offer == null) {
                    return;
                }
                double free = freePlaces(level, accountId, offer.product) - required(account).getOrDefault(offer.product, 0);
                if (free < offer.accounts) {
                    OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.contract.msg.no_places",
                            offer.accounts, Component.translatable(offer.product.translationKey()), (int) Math.max(0, Math.floor(free)))
                            .withStyle(ChatFormatting.GOLD));
                    return;
                }
                int maxActive = maxActive(server, accountId);
                if (account.active.size() >= maxActive) {
                    OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.contract.msg.full", maxActive)
                            .withStyle(ChatFormatting.GOLD));
                    return;
                }
                account.offers.remove(offer);
                account.active.add(offer);
                OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.contract.msg.accepted",
                        clientName(offer).copy().withColor(color(offer.rarity)), offer.accounts, Component.translatable(offer.product.translationKey()), offer.days));
            }
            case CANCEL -> {
                ContractBook.Contract running = find(account.active, contractId);
                if (running == null) {
                    return;
                }
                account.active.remove(running);
                charge(level, accountId, running);
                OwnerMessages.toOwner(server, accountId, Component.translatable("data_center_addon.contract.msg.cancelled",
                        clientName(running), Money.usd(running.penaltyCents), running.reputationLoss()).withStyle(ChatFormatting.RED));
            }
        }
        book.setDirty();
    }

    private static ContractBook.Contract find(List<ContractBook.Contract> list, int id) {
        for (ContractBook.Contract c : list) {
            if (c.id == id) {
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ snapshot

    /** The account's running contracts and offers as the manager shows them. */
    public static List<HostingSnapshot.Contract> snapshot(ServerLevel level, UUID accountId) {
        if (!Config.contractsEnabled()) {
            return List.of();
        }
        ContractBook.Account account = account(level, accountId);
        long today = DataCenterApi.currentDate(level).toEpochDay();
        Map<HostingProduct, Integer> required = required(account);
        Map<HostingProduct, Double> free = new EnumMap<>(HostingProduct.class);
        List<HostingSnapshot.Contract> list = new ArrayList<>();
        for (ContractBook.Contract c : account.active) {
            double f = free.computeIfAbsent(c.product, p -> freePlaces(level, accountId, p));
            float metToday = c.samples == 0 ? 1f : (float) c.samplesMet / c.samples;
            list.add(new HostingSnapshot.Contract(c.id, false, c.client, c.product.ordinal(), c.accounts, c.days, c.daysDone,
                    c.strikes, c.centsPerDay, c.bonusCents, c.penaltyCents, c.reputation, f, required.get(c.product), metToday, 0,
                    c.urgent, c.mini, c.rarity.ordinal()));
        }
        for (ContractBook.Contract c : account.offers) {
            double f = free.computeIfAbsent(c.product, p -> freePlaces(level, accountId, p));
            int need = required.getOrDefault(c.product, 0) + c.accounts;
            list.add(new HostingSnapshot.Contract(c.id, true, c.client, c.product.ordinal(), c.accounts, c.days, 0, 0,
                    c.centsPerDay, c.bonusCents, c.penaltyCents, c.reputation, f, need, 1f, (int) Math.max(0, c.expiresDay - today + 1),
                    c.urgent, c.mini, c.rarity.ordinal()));
        }
        return List.copyOf(list);
    }

    // ------------------------------------------------------------------ text

    private static Component clientName(ContractBook.Contract c) {
        return clientName(c.client);
    }

    /** Text colour of a rarity, the same as the computer parts' tiers (grey, green, blue, purple, orange). */
    public static int color(ContractRarity rarity) {
        return switch (rarity) {
            case COMMON -> 0xC6C6C6;
            case UNCOMMON -> 0x62D96B;
            case RARE -> 0x4FA3FF;
            case EPIC -> 0xBF73FF;
            case LEGENDARY -> 0xFFAE3D;
        };
    }

    /** A client's name (contracts and delivery orders share the clients). */
    public static Component clientName(int client) {
        return Component.translatable("data_center_addon.contract.client." + client);
    }

}
