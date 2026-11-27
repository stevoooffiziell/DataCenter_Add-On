package de.f4ked.data_center_addon.hosting.startup;

import java.util.Locale;

/**
 * One-time rewards for a young hosting company ({@link StartupService}): customer counts, customer
 * classes and the first finished contract and delivery. Paid once per account; an
 * account that already meets one when the add-on first sees it gets it marked without the reward.
 */
public enum Milestone {
    CUSTOMERS_10(Kind.CUSTOMERS, 10, 250, 5),
    CUSTOMERS_50(Kind.CUSTOMERS, 50, 750, 10),
    CUSTOMERS_100(Kind.CUSTOMERS, 100, 1_500, 20),
    CUSTOMERS_500(Kind.CUSTOMERS, 500, 5_000, 40),
    CUSTOMERS_1000(Kind.CUSTOMERS, 1_000, 10_000, 60),
    CLASS_SMALL_BUSINESS(Kind.CLASS, 1, 1_000, 0),
    CLASS_MID_MARKET(Kind.CLASS, 2, 3_000, 0),
    CLASS_ENTERPRISE(Kind.CLASS, 3, 7_500, 0),
    CLASS_FINANCE(Kind.CLASS, 4, 15_000, 0),
    FIRST_CONTRACT(Kind.EVENT, 1, 500, 10),
    FIRST_DELIVERY(Kind.EVENT, 1, 500, 10);

    public enum Kind { CUSTOMERS, CLASS, EVENT }

    public final Kind kind;
    /** Customers, customer class ordinal, or 1 for events. */
    public final int threshold;
    /** Reward in dollars before the config multiplier. */
    public final int usd;
    /** Renown (Bekanntheit) it brings with the add-on's reputation model. */
    public final int renown;

    Milestone(Kind kind, int threshold, int usd, int renown) {
        this.kind = kind;
        this.threshold = threshold;
        this.usd = usd;
        this.renown = renown;
    }

    public long bit() {
        return 1L << ordinal();
    }

    public String translationKey() {
        return "data_center_addon.startup.milestone." + name().toLowerCase(Locale.ROOT);
    }
}
