package de.f4ked.data_center_addon;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;

/**
 * Common config, stored in {@code config/data_center_addon-common.toml}.
 * <p>
 * Part definitions are data-pack registry entries, so changes to the part options only take effect
 * the next time a world is loaded (leave the world and join it again).
 */
public final class Config {
    /** Shop categories whose original Data Center parts are hidden by default when hiding is on. */
    public static final List<String> DEFAULT_HIDDEN_CATEGORIES = List.of(
            "CPU_AMD", "CPU_INTEL", "CPU_SERVER",
            "GPU_NVIDIA", "GPU_AMD", "GPU_DATACENTER", "GPU_MINING",
            "RAM", "STORAGE", "PSU", "MAINBOARD", "MAINBOARD_MINING");

    /**
     * The default up to version 1.1.0, when the add-on had no real board for every socket yet. A config
     * that still holds exactly this list is moved to the new default once ({@link #onLoad}).
     */
    private static final List<String> OLD_DEFAULT_HIDDEN_CATEGORIES = List.of(
            "CPU_AMD", "CPU_INTEL", "CPU_SERVER",
            "GPU_NVIDIA", "GPU_AMD", "GPU_DATACENTER", "GPU_MINING",
            "RAM", "STORAGE", "PSU");

    private static final List<String> ALL_PART_CATEGORIES = List.of(
            "CPU_AMD", "CPU_INTEL", "CPU_SERVER",
            "GPU_NVIDIA", "GPU_AMD", "GPU_DATACENTER", "GPU_MINING",
            "RAM", "STORAGE", "PSU", "MAINBOARD", "MAINBOARD_MINING", "FAN");

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue HIDE_ORIGINAL_PARTS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> HIDDEN_CATEGORIES;
    private static final ModConfigSpec.DoubleValue HOSTING_STORAGE_BOOST;
    private static final ModConfigSpec.BooleanValue CONTRACTS_ENABLED;
    private static final ModConfigSpec.DoubleValue CONTRACT_PAY;
    private static final ModConfigSpec.BooleanValue DELIVERIES_ENABLED;
    private static final ModConfigSpec.BooleanValue HIDE_ORIGINAL_NETWORK;
    private static final ModConfigSpec.BooleanValue SWITCHES_NEED_POWER;
    private static final ModConfigSpec.BooleanValue CABLE_REACH_LIMITS;
    private static final ModConfigSpec.BooleanValue INSTALL_QUESTS;
    private static final ModConfigSpec.DoubleValue START_BONUS;
    private static final ModConfigSpec.EnumValue<StartBonusMode> START_BONUS_MODE;
    private static final ModConfigSpec.IntValue START_BONUS_DAYS;
    private static final ModConfigSpec.IntValue START_BONUS_REVENUE;
    private static final ModConfigSpec.IntValue FOUNDING_GRANT;
    private static final ModConfigSpec.IntValue FOUNDING_LOAN;
    private static final ModConfigSpec.IntValue FOUNDING_LOAN_GRACE;
    private static final ModConfigSpec.IntValue FOUNDING_LOAN_REPAY;
    private static final ModConfigSpec.IntValue VETERAN_REPUTATION;
    private static final ModConfigSpec.DoubleValue SETUP_FEE;
    private static final ModConfigSpec.DoubleValue MILESTONE_REWARDS;
    private static final ModConfigSpec.BooleanValue SERVICE_TICKETS;
    private static final ModConfigSpec.DoubleValue PREPAY_SHARE;
    private static final ModConfigSpec.DoubleValue PREPAY_DISCOUNT;
    private static final ModConfigSpec.BooleanValue MARKET_MODEL;
    private static final ModConfigSpec.DoubleValue CLOSE_RATE;
    private static final ModConfigSpec.DoubleValue WORD_OF_MOUTH;
    private static final ModConfigSpec.DoubleValue CHURN_GOOD;
    private static final ModConfigSpec.DoubleValue CHURN_BAD;
    private static final ModConfigSpec.DoubleValue WAIT_DAYS;
    private static final ModConfigSpec.BooleanValue SHARED_POOL;
    private static final ModConfigSpec.BooleanValue REPUTATION_MODEL;
    private static final ModConfigSpec.DoubleValue ORDER_RENOWN;
    private static final ModConfigSpec.BooleanValue RUF_PER_PRODUCT;
    private static final ModConfigSpec.BooleanValue LOSS_NOTICES;
    private static final ModConfigSpec.DoubleValue PURCHASE_PRICES;

    /** How the start bonus ends. */
    public enum StartBonusMode { TIME, REVENUE }

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Real computer parts added by this add-on.").push("parts");
        HIDE_ORIGINAL_PARTS = builder
                .comment("Hide Data Center's own (fictional brand) parts from the Shop, so only the real parts",
                        "of this add-on are for sale. Parts you already own keep working.",
                        "Takes effect the next time a world is loaded.")
                .define("hideOriginalParts", false);
        HIDDEN_CATEGORIES = builder
                .comment("Shop categories in which the original parts are hidden when hideOriginalParts is true.",
                        "Fans are kept by default.",
                        "Allowed: " + String.join(", ", ALL_PART_CATEGORIES))
                .defineListAllowEmpty("hiddenCategories", DEFAULT_HIDDEN_CATEGORIES, () -> "CPU_AMD",
                        o -> o instanceof String s && ALL_PART_CATEGORIES.contains(s));
        builder.pop();

        builder.comment("Hosting racks.").push("hosting");
        HOSTING_STORAGE_BOOST = builder
                .comment("Capacity multiplier for this add-on's storage parts while their server runs in a Hosting Rack",
                        "(more storage to rent out in dedicated mode, more shared capacity in shared mode).",
                        "1.0 turns the boost off.")
                .defineInRange("storageBoost", 1.10, 1.0, 2.0);
        CONTRACTS_ENABLED = builder
                .comment("Hosting contracts in the Hosting Manager: clients that want places of one product kept free",
                        "for a number of in-game days. Paid daily, with a bonus at the end and a penalty if they fail.")
                .define("contracts", true);
        CONTRACT_PAY = builder
                .comment("Multiplier for what contracts pay (daily pay, bonus and penalty).")
                .defineInRange("contractPayMultiplier", 1.0, 0.1, 10.0);
        DELIVERIES_ENABLED = builder
                .comment("Delivery orders in the Hosting Manager (preview): clients that want servers, parts, cables or",
                        "switches delivered before a deadline. Uses contractPayMultiplier too.")
                .define("deliveries", true);
        builder.pop();

        builder.comment("The founding phase of a hosting company: help to get the first money together.").push("startup");
        START_BONUS = builder
                .comment("Extra share of every hosting rack's income paid on top while the start bonus runs.",
                        "2.0 = the racks earn three times as much. 0 turns the start bonus off.")
                .defineInRange("startBonus", 2.0, 0.0, 10.0);
        START_BONUS_MODE = builder
                .comment("TIME: the bonus starts with the first rack income and fades out over startBonusDays in-game days.",
                        "REVENUE: the full bonus until the racks have earned startBonusRevenueUsd, then it ends.")
                .defineEnum("startBonusMode", StartBonusMode.TIME);
        START_BONUS_DAYS = builder
                .comment("In-game days the TIME start bonus fades out over.")
                .defineInRange("startBonusDays", 120, 1, 3650);
        START_BONUS_REVENUE = builder
                .comment("Rack income in dollars until which the REVENUE start bonus runs.")
                .defineInRange("startBonusRevenueUsd", 50_000, 1, 100_000_000);
        FOUNDING_GRANT = builder
                .comment("Founding grant in dollars, paid once with the first rack income. 0 turns it off.")
                .defineInRange("foundingGrantUsd", 2_000, 0, 10_000_000);
        FOUNDING_LOAN = builder
                .comment("Founding loan in dollars, interest-free, taken once in the Hosting Manager. 0 turns it off.")
                .defineInRange("foundingLoanUsd", 10_000, 0, 100_000_000);
        FOUNDING_LOAN_GRACE = builder
                .comment("In-game days after taking the founding loan before the first instalment is due.")
                .defineInRange("foundingLoanGraceDays", 30, 0, 3650);
        FOUNDING_LOAN_REPAY = builder
                .comment("Daily instalments the founding loan is paid back in.")
                .defineInRange("foundingLoanRepayDays", 60, 1, 3650);
        VETERAN_REPUTATION = builder
                .comment("An account that already has this much hosting reputation when the add-on first sees it is an",
                        "established company: no start bonus and no founding grant.")
                .defineInRange("veteranReputation", 150, 0, 1000);
        SETUP_FEE = builder
                .comment("One-time setup fee every new customer pays, in months of the product's list price. 0 turns it off.")
                .defineInRange("setupFeeMonths", 1.0, 0.0, 12.0);
        MILESTONE_REWARDS = builder
                .comment("Multiplier for the milestone rewards (customers, customer classes, first contract, ...). 0 turns them off.")
                .defineInRange("milestoneRewards", 1.0, 0.0, 100.0);
        PREPAY_SHARE = builder
                .comment("Share of new customers that book an annual plan and pay 3 to 12 months in advance (at a discount).",
                        "The money arrives at once and is settled day by day against the income they bring; customers",
                        "that leave early get the rest back. 0 turns prepayment off.")
                .defineInRange("prepayShare", 0.3, 0.0, 1.0);
        PREPAY_DISCOUNT = builder
                .comment("Discount a prepaying customer gets.")
                .defineInRange("prepayDiscount", 0.05, 0.0, 0.5);
        SERVICE_TICKETS = builder
                .comment("Service tickets: clients ask you to look at one of your racks. Right-click the rack before the",
                        "deadline to be paid.")
                .define("serviceTickets", true);
        builder.pop();

        builder.comment("The hosting market: where new customers come from.").push("market");
        MARKET_MODEL = builder
                .comment("Use the add-on's market model for shared hosting, VPS and colocation customers: each product has",
                        "a pool of potential customers, you reach a share of it, and new customers close the gap.",
                        "false: customers arrive the way Data Center does it (the Market tab still shows the market).")
                .define("marketModel", true);
        CLOSE_RATE = builder
                .comment("Share of the gap between your reachable potential and your customers that becomes customers",
                        "per game month. 0.25 = 63 % of the gap is closed in about 4 game months.")
                .defineInRange("closeRate", 0.25, 0.01, 1.0);
        WORD_OF_MOUTH = builder
                .comment("Word of mouth: new customers per game month that every customer brings, while the reachable",
                        "potential is not used up (logistic growth). 0 turns it off.")
                .defineInRange("wordOfMouth", 0.15, 0.0, 1.0);
        CHURN_GOOD = builder
                .comment("Cancellations per game month with perfect service (no downtime, no overload, no throttling).",
                        "Data Center's usual value is about 0.03. Segments and events still shift it up or down.")
                .defineInRange("churnGood", 0.01, 0.0, 0.5);
        CHURN_BAD = builder
                .comment("Cancellations per game month with bad service (servers down, overloaded or throttled).")
                .defineInRange("churnBad", 0.08, 0.0, 1.0);
        WAIT_DAYS = builder
                .comment("New customers who find no free place wait this many in-game days on average before they give",
                        "up, instead of being lost at once. They get the next free places. 0 turns the waiting list off.")
                .defineInRange("waitDays", 7.0, 0.0, 60.0);
        SHARED_POOL = builder
                .comment("Multiplayer: accounts that sell the same product share its pool and compete through reputation,",
                        "customer classes, awareness and price. false: every account has the whole pool to itself.")
                .define("sharedPool", false);
        builder.pop();

        builder.comment("Hosting reputation: how it is earned and lost.").push("reputation");
        REPUTATION_MODEL = builder
                .comment("Use the add-on's reputation model: running servers no longer earn reputation points by the hour;",
                        "reputation comes from events instead. false: Data Center's own reputation.")
                .define("reputationModel", true);
        ORDER_RENOWN = builder
                .comment("Multiplier for the reputation (renown) contracts and delivery orders give and cost, with the",
                        "reputation model on. Orders are meant to be the main source of renown.")
                .defineInRange("orderRenown", 3.0, 0.0, 20.0);
        RUF_PER_PRODUCT = builder
                .comment("Reputation per product: the share of a product's market you reach follows half your company's",
                        "Ruf and half the Ruf of that product's own service. A good game server Ruf then helps little",
                        "with colocation.")
                .define("rufPerProduct", false);
        LOSS_NOTICES = builder
                .comment("Chat notices when a rack costs Ruf: a crash, servers going down, throttling (each at most once a",
                        "game day per rack).")
                .define("lossNotices", true);
        builder.pop();

        builder.comment("Industry: buying raw materials, processing them in factories and selling the components.").push("industry");
        PURCHASE_PRICES = builder
                .comment("Multiplier for the prices of the raw materials sold by the Purchase Terminal.")
                .defineInRange("purchasePrices", 1.0, 0.1, 10.0);
        builder.pop();

        builder.comment("Network cables (CAT, fibre) and switches added by this add-on.").push("network");
        HIDE_ORIGINAL_NETWORK = builder
                .comment("Hide Data Center's own cables and switches: its switch leaves the Shop and its cable recipes",
                        "are disabled. Cables and switches already placed keep working.",
                        "Takes effect the next time a world is loaded.")
                .define("hideOriginalNetwork", false);
        SWITCHES_NEED_POWER = builder
                .comment("Switches need power (FE, their real wattage). A switch without power cuts its network.")
                .define("switchesNeedPower", true);
        CABLE_REACH_LIMITS = builder
                .comment("A cable run longer than the cable's real reach (e.g. 100 blocks for CAT5e, 30 for CAT8)",
                        "falls back to a tenth of its speed. Switches start a new run.")
                .define("cableReachLimits", true);
        builder.pop();

        builder.comment("FTB Quests guide and release calendar.").push("quests");
        INSTALL_QUESTS = builder
                .comment("Add the add-on's chapters (a guide and a release calendar) to the FTB Quests quest book when a",
                        "world starts. They are only rewritten when the add-on brings a new version of them; other",
                        "chapters are never touched. Turn this off to keep your own edits of these chapters.")
                .define("installQuests", true);
        builder.pop();

        SPEC = builder.build();
    }

    private Config() {
    }

    /**
     * Configs written by 1.1.0 and older kept Data Center's boards. If the list is still that old default
     * (the player never changed it), it becomes the new default, which hides the original boards too.
     */
    public static void onLoad(ModConfigEvent.Loading event) {
        ModConfig config = event.getConfig();
        if (config.getSpec() != SPEC) {
            return;
        }
        if (new HashSet<>(HIDDEN_CATEGORIES.get()).equals(new HashSet<>(OLD_DEFAULT_HIDDEN_CATEGORIES))) {
            HIDDEN_CATEGORIES.set(DEFAULT_HIDDEN_CATEGORIES);
            SPEC.save();
        }
    }

    public static boolean hidesOriginal(String category) {
        if (!SPEC.isLoaded()) {
            return false;
        }
        return HIDE_ORIGINAL_PARTS.get() && HIDDEN_CATEGORIES.get().contains(category);
    }

    public static boolean hidesOriginalNetwork() {
        return SPEC.isLoaded() && HIDE_ORIGINAL_NETWORK.get();
    }

    public static boolean switchesNeedPower() {
        return !SPEC.isLoaded() || SWITCHES_NEED_POWER.get();
    }

    public static boolean cableReachLimits() {
        return !SPEC.isLoaded() || CABLE_REACH_LIMITS.get();
    }

    public static boolean installQuests() {
        return !SPEC.isLoaded() || INSTALL_QUESTS.get();
    }

    public static boolean contractsEnabled() {
        return !SPEC.isLoaded() || CONTRACTS_ENABLED.get();
    }

    public static boolean deliveriesEnabled() {
        return !SPEC.isLoaded() || DELIVERIES_ENABLED.get();
    }

    public static double contractPayMultiplier() {
        return SPEC.isLoaded() ? CONTRACT_PAY.get() : 1.0;
    }

    public static double startBonus() {
        return SPEC.isLoaded() ? START_BONUS.get() : 2.0;
    }

    public static StartBonusMode startBonusMode() {
        return SPEC.isLoaded() ? START_BONUS_MODE.get() : StartBonusMode.TIME;
    }

    public static int startBonusDays() {
        return SPEC.isLoaded() ? START_BONUS_DAYS.get() : 120;
    }

    public static long startBonusRevenueCents() {
        return (SPEC.isLoaded() ? START_BONUS_REVENUE.get() : 50_000) * 100L;
    }

    public static long foundingGrantCents() {
        return (SPEC.isLoaded() ? FOUNDING_GRANT.get() : 2_000) * 100L;
    }

    public static long foundingLoanCents() {
        return (SPEC.isLoaded() ? FOUNDING_LOAN.get() : 10_000) * 100L;
    }

    public static int foundingLoanGraceDays() {
        return SPEC.isLoaded() ? FOUNDING_LOAN_GRACE.get() : 30;
    }

    public static int foundingLoanRepayDays() {
        return SPEC.isLoaded() ? FOUNDING_LOAN_REPAY.get() : 60;
    }

    public static int veteranReputation() {
        return SPEC.isLoaded() ? VETERAN_REPUTATION.get() : 150;
    }

    public static double setupFeeMonths() {
        return SPEC.isLoaded() ? SETUP_FEE.get() : 1.0;
    }

    public static double milestoneRewards() {
        return SPEC.isLoaded() ? MILESTONE_REWARDS.get() : 1.0;
    }

    public static double prepayShare() {
        return SPEC.isLoaded() ? PREPAY_SHARE.get() : 0.3;
    }

    public static double prepayDiscount() {
        return SPEC.isLoaded() ? PREPAY_DISCOUNT.get() : 0.05;
    }

    public static boolean serviceTickets() {
        return !SPEC.isLoaded() || SERVICE_TICKETS.get();
    }

    public static boolean marketModel() {
        return !SPEC.isLoaded() || MARKET_MODEL.get();
    }

    public static double closeRate() {
        return SPEC.isLoaded() ? CLOSE_RATE.get() : 0.25;
    }

    public static double wordOfMouth() {
        return SPEC.isLoaded() ? WORD_OF_MOUTH.get() : 0.15;
    }

    public static double churnGood() {
        return SPEC.isLoaded() ? CHURN_GOOD.get() : 0.01;
    }

    public static double churnBad() {
        return SPEC.isLoaded() ? Math.max(CHURN_BAD.get(), CHURN_GOOD.get()) : 0.08;
    }

    public static double waitDays() {
        return SPEC.isLoaded() ? WAIT_DAYS.get() : 7.0;
    }

    public static boolean sharedPool() {
        return SPEC.isLoaded() && SHARED_POOL.get();
    }

    public static boolean reputationModel() {
        return !SPEC.isLoaded() || REPUTATION_MODEL.get();
    }

    /** The multiplier for order reputation; 1 with the reputation model off. */
    public static double orderRenown() {
        if (!reputationModel()) {
            return 1.0;
        }
        return SPEC.isLoaded() ? ORDER_RENOWN.get() : 3.0;
    }

    public static boolean rufPerProduct() {
        return reputationModel() && SPEC.isLoaded() && RUF_PER_PRODUCT.get();
    }

    public static boolean lossNotices() {
        return reputationModel() && (!SPEC.isLoaded() || LOSS_NOTICES.get());
    }

    public static double purchasePrices() {
        return SPEC.isLoaded() ? PURCHASE_PRICES.get() : 1.0;
    }

    public static double hostingStorageBoost() {
        return SPEC.isLoaded() ? HOSTING_STORAGE_BOOST.get() : 1.0;
    }
}
