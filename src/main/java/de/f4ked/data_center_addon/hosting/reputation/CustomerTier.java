package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.certification.CertificationTier;
import com.example.datacenter.hosting.CustomerClass;

import java.util.Locale;

/**
 * The add-on's customer classes: a finer ladder over Data Center's five {@link CustomerClass}es, up to a
 * renown of {@link ClassRules#MAX_RENOWN}. Every class belongs to one of Data Center's classes
 * ({@link #base}), which is what Data Center sees (room certification, rack limits, advancements); the
 * add-on uses the class itself for income, market, orders and the Hosting Manager.
 *
 * @param base   the Data Center class it belongs to
 * @param room   the room certification a rack needs to serve it
 * @param ruf    the Ruf it needs
 * @param renown the renown it needs
 * @param income multiplier on hosting income
 * @param pool   size of its sub-pool of the market, relative to the hobbyist pool
 */
public enum CustomerTier {
    HOBBYISTS(CustomerClass.HOBBYISTS, CertificationTier.NONE, 0, 0, 1.00, 1.00),
    FREELANCERS(CustomerClass.HOBBYISTS, CertificationTier.NONE, 0.75, 50, 1.04, 0.15),
    SMALL_BUSINESS(CustomerClass.SMALL_BUSINESS, CertificationTier.NONE, 0.80, 100, 1.08, 0.15),
    STARTUPS(CustomerClass.SMALL_BUSINESS, CertificationTier.NONE, 0.85, 150, 1.12, 0.15),
    MID_MARKET(CustomerClass.MID_MARKET, CertificationTier.TIER_I, 0.90, 200, 1.18, 0.15),
    E_COMMERCE(CustomerClass.MID_MARKET, CertificationTier.TIER_I, 0.92, 300, 1.24, 0.15),
    ENTERPRISE(CustomerClass.ENTERPRISE, CertificationTier.TIER_II, 0.95, 400, 1.30, 0.15),
    CORPORATIONS(CustomerClass.ENTERPRISE, CertificationTier.TIER_II, 0.96, 500, 1.37, 0.10),
    FINANCE(CustomerClass.FINANCE, CertificationTier.TIER_III, 0.98, 600, 1.45, 0.10),
    HEALTHCARE(CustomerClass.FINANCE, CertificationTier.TIER_III, 0.985, 850, 1.55, 0.08),
    GOVERNMENT(CustomerClass.FINANCE, CertificationTier.TIER_III, 0.99, 1150, 1.65, 0.08),
    TELECOM(CustomerClass.FINANCE, CertificationTier.TIER_IV, 0.99, 1500, 1.76, 0.07),
    HYPERSCALERS(CustomerClass.FINANCE, CertificationTier.TIER_IV, 0.993, 1950, 1.88, 0.06),
    CRITICAL_INFRASTRUCTURE(CustomerClass.FINANCE, CertificationTier.TIER_IV, 0.995, 2400, 2.00, 0.05);

    private static final CustomerTier[] VALUES = values();

    public final CustomerClass base;
    public final CertificationTier room;
    public final double ruf, renown, income, pool;

    CustomerTier(CustomerClass base, CertificationTier room, double ruf, double renown, double income, double pool) {
        this.base = base;
        this.room = room;
        this.ruf = ruf;
        this.renown = renown;
        this.income = income;
        this.pool = pool;
    }

    public String translationKey() {
        return "data_center_addon.customer_tier." + name().toLowerCase(Locale.ROOT);
    }

    /** The class at {@code ordinal}, clamped to the ladder. */
    public static CustomerTier of(int ordinal) {
        return VALUES[Math.max(0, Math.min(VALUES.length - 1, ordinal))];
    }

    /** The first class of a Data Center class (what an account held before the finer ladder). */
    public static CustomerTier first(CustomerClass base) {
        for (CustomerTier t : VALUES) {
            if (t.base == base) {
                return t;
            }
        }
        return HOBBYISTS;
    }

    /** The next class, or null at the top. */
    public CustomerTier next() {
        return ordinal() + 1 < VALUES.length ? VALUES[ordinal() + 1] : null;
    }

    public boolean top() {
        return ordinal() == VALUES.length - 1;
    }

    /**
     * The class a rack serves: the best class up to {@code unlocked} that the rack's Data Center class
     * (after its room and its own limit) and its room's certification allow.
     */
    public static CustomerTier served(CustomerTier unlocked, CustomerClass rackClass, CertificationTier room) {
        CustomerTier best = HOBBYISTS;
        for (int i = 0; i <= unlocked.ordinal(); i++) {
            CustomerTier t = VALUES[i];
            if (t.base.ordinal() <= rackClass.ordinal() && t.room.level <= room.level) {
                best = t;
            }
        }
        return best;
    }
}
