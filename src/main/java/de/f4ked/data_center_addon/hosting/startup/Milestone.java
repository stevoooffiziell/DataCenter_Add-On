package de.f4ked.data_center_addon.hosting.startup;

import java.util.Locale;

/**
 * One-time rewards for a young hosting company ({@link StartupService}): customer counts, customer
 * classes and the first finished contract, delivery and service ticket. Paid once per account; an
 * account that already meets one when the add-on first sees it gets it marked without the reward.
 */
public enum Milestone {
    CUSTOMERS_10(Kind.CUSTOMERS, 10, 250),
    CUSTOMERS_50(Kind.CUSTOMERS, 50, 750),
    CUSTOMERS_100(Kind.CUSTOMERS, 100, 1_500),
    CUSTOMERS_500(Kind.CUSTOMERS, 500, 5_000),
    CUSTOMERS_1000(Kind.CUSTOMERS, 1_000, 10_000),
    CLASS_SMALL_BUSINESS(Kind.CLASS, 1, 1_000),
    CLASS_MID_MARKET(Kind.CLASS, 2, 3_000),
    CLASS_ENTERPRISE(Kind.CLASS, 3, 7_500),
    CLASS_FINANCE(Kind.CLASS, 4, 15_000),
    FIRST_CONTRACT(Kind.EVENT, 1, 500),
    FIRST_DELIVERY(Kind.EVENT, 1, 500),
    FIRST_TICKET(Kind.EVENT, 1, 250);

    public enum Kind { CUSTOMERS, CLASS, EVENT }

    public final Kind kind;
    /** Customers, customer class ordinal, or 1 for events. */
    public final int threshold;
    /** Reward in dollars before the config multiplier. */
    public final int usd;

    Milestone(Kind kind, int threshold, int usd) {
        this.kind = kind;
        this.threshold = threshold;
        this.usd = usd;
    }

    public long bit() {
        return 1L << ordinal();
    }

    public String translationKey() {
        return "data_center_addon.startup.milestone." + name().toLowerCase(Locale.ROOT);
    }
}
