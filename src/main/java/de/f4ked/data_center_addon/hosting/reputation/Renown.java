package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.hosting.HostingCustomers;
import com.example.datacenter.hosting.HostingProduct;
import de.f4ked.data_center_addon.Config;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Bekanntheit (renown): how well known the account is. It is Data Center's reputation points (0 to
 * 1000), but with the reputation model on they only grow through what the account does:
 * <ul>
 *   <li>finished contracts and delivery orders,</li>
 *   <li>new customers: {@link #PER_CUSTOMER} for every customer above the most it ever had, once that
 *       customer has had its first {@link #FIRST_MONTH_DAYS} days without a disruption,</li>
 *   <li>milestones,</li>
 *   <li>failures fixed fast ({@link QuickFix}),</li>
 *   <li>a higher room certification ({@link Certification}),</li>
 *   <li>customer reviews, good or bad ({@link Reviews}),</li>
 *   <li>advertising ({@link Campaigns}),</li>
 * </ul>
 * and fall through failed or cancelled orders. Renown sets the share of the markets the account reaches.
 * Every change is booked by its source, for the Reputation tab.
 */
public final class Renown {
    /** Renown for every new customer whose first month went without a disruption (one point per 10 customers). */
    static final double PER_CUSTOMER = 0.1;
    /** The first month of a new customer, in in-game days. */
    static final int FIRST_MONTH_DAYS = 30;
    /** A day with more downtime than this, or with a crash, disrupts every new customer in its first month. */
    static final double DISRUPTION_DOWN = 0.01;

    /** Where renown comes from; the name gives {@code data_center_addon.reputation.source.<name>}. */
    public enum Source {
        ORDERS, CUSTOMERS, MILESTONES, QUICK_FIX, CERTIFICATION, REVIEWS, ADVERTISING, PENALTIES;

        public String key() {
            return "data_center_addon.reputation.source." + name().toLowerCase(Locale.ROOT);
        }
    }

    private Renown() {
    }

    /** Changes the account's renown and books it by source. */
    public static void add(ServerLevel level, @Nullable UUID account, double amount, Source source) {
        if (amount == 0 || Double.isNaN(amount)) {
            return;
        }
        DataCenterApi.changeReputation(level, account, amount);
        if (Config.reputationModel()) {
            ReputationBook book = ReputationBook.get(level.getServer());
            book.account(account).sources.merge(source, amount, Double::sum);
            book.setDirty();
        }
    }

    /** Customers above the account's high-water mark start their first month (checked once a second). */
    static void customers(ServerLevel level, UUID id, ReputationBook.Account a, long today) {
        HostingCustomers book = HostingCustomers.get(level);
        double total = 0;
        for (HostingProduct product : HostingProduct.values()) {
            total += book.total(id, product);
        }
        if (Double.isNaN(a.customerMark)) {
            a.customerMark = total;
        } else if (total > a.customerMark + 1) {
            double[] last = a.cohorts.peekLast();
            if (last != null && last[0] == today && last[2] == 0) {
                last[1] += total - a.customerMark;
            } else {
                a.cohorts.addLast(new double[]{today, total - a.customerMark, 0});
            }
            a.customerMark = total;
        }
    }

    /**
     * A finished day: a disrupted day spoils the first month of every new customer in it; customers
     * whose first month is over without a disruption bring their renown.
     *
     * @param day the finished day's record ({@link Ruf#SERVED}, ...)
     */
    static void closeDay(ServerLevel level, UUID id, ReputationBook.Account a, double[] day, long today) {
        double total = day[Ruf.SERVED] + day[Ruf.DOWN];
        boolean disrupted = day[Ruf.CRASHES] > 0 || (total > 0 && day[Ruf.DOWN] / total > DISRUPTION_DOWN);
        if (disrupted) {
            for (double[] cohort : a.cohorts) {
                cohort[2] = 1;
            }
        }
        while (!a.cohorts.isEmpty() && today - a.cohorts.peekFirst()[0] >= FIRST_MONTH_DAYS) {
            double[] cohort = a.cohorts.removeFirst();
            if (cohort[2] == 0) {
                add(level, id, cohort[1] * PER_CUSTOMER, Source.CUSTOMERS);
            }
        }
    }

    /** New customers still in an undisturbed first month. */
    static double pending(ReputationBook.Account a) {
        double sum = 0;
        for (double[] cohort : a.cohorts) {
            if (cohort[2] == 0) {
                sum += cohort[1];
            }
        }
        return sum;
    }
}
