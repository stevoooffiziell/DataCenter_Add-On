package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.hosting.CustomerClass;

/**
 * Which customer class an account has unlocked, from both values: the Ruf (quality) and the renown
 * (Bekanntheit). A class becomes a goal to work for instead of a time to wait.
 * <p>
 * The room certification each class needs stays as in Data Center: a rack only serves a class its room's
 * tier allows (Mid-Market Tier I, Enterprise Tier II, Finance Tier III).
 * <p>
 * Once unlocked, a class is kept while the Ruf stays within {@link #RUF_MARGIN} and the renown within
 * {@link #RENOWN_MARGIN} of its requirement.
 */
public final class ClassRules {
    /** Ruf and renown each class needs, in {@link CustomerClass} order. */
    public static final double[] RUF = {0, 0.80, 0.90, 0.95, 0.98};
    public static final double[] RENOWN = {0, 100, 200, 400, 600};
    static final double RUF_MARGIN = 0.05, RENOWN_MARGIN = 25;

    private ClassRules() {
    }

    public static boolean meets(CustomerClass c, double ruf, double renown) {
        return ruf >= RUF[c.ordinal()] - 1e-9 && renown >= RENOWN[c.ordinal()];
    }

    /** The class the account holds now, given the class it had. */
    public static CustomerClass classFor(double ruf, double renown, CustomerClass current) {
        CustomerClass best = CustomerClass.HOBBYISTS;
        for (CustomerClass c : CustomerClass.values()) {
            if (meets(c, ruf, renown)) {
                best = c;
            }
        }
        if (best.ordinal() < current.ordinal()
                && ruf >= RUF[current.ordinal()] - RUF_MARGIN && renown >= RENOWN[current.ordinal()] - RENOWN_MARGIN) {
            return current;
        }
        return best;
    }
}
