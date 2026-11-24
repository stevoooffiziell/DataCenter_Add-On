package de.f4ked.data_center_addon.hosting.startup;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.hosting.HostingCustomers;
import com.example.datacenter.hosting.HostingModeRates;
import com.example.datacenter.hosting.HostingProduct;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.server.level.ServerLevel;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.UUID;

/**
 * Annual plans paid in advance. Part of the new customers ({@link Config#prepayShare()}) pay 3 to 12
 * months at once, minus a discount. Data Center keeps paying for them every month, so the add-on
 * settles the prepaid value day by day against the wallet; customers that leave early get the rest
 * back. What the wallet cannot cover stays open and is taken later.
 */
final class Prepayments {
    private static final int MONTHS_MIN = 3;
    private static final int MONTHS_MAX = 12;
    private static final double DAYS_PER_MONTH = 30.4375;

    private Prepayments() {
    }

    /** {@code joined} new customers of a product arrived; whole customers out of the share book a plan. */
    static void joined(ServerLevel level, UUID id, StartupBook.Account a, HostingProduct product, double joined, LocalDate date,
                       double classMultiplier) {
        if (!a.prepayEnabled || Config.prepayShare() <= 0) {
            return;
        }
        int i = product.ordinal();
        a.prepayPending[i] += joined * Config.prepayShare();
        if (a.prepayPending[i] >= 1) {
            int count = (int) Math.floor(a.prepayPending[i]);
            a.prepayPending[i] -= count;
            book(level, id, a, product, count, date, classMultiplier);
        }
    }

    /** {@code count} customers pay several months in advance: the money arrives now, minus the discount. */
    private static void book(ServerLevel level, UUID id, StartupBook.Account a, HostingProduct product, int count, LocalDate date,
                             double classMultiplier) {
        int months = MONTHS_MIN + level.getRandom().nextInt(MONTHS_MAX - MONTHS_MIN + 1);
        double value = count * HostingModeRates.unitNet(product, date) * 100 * months * classMultiplier;
        long paid = Math.round(value * (1 - Config.prepayDiscount()));
        if (paid <= 0) {
            return;
        }
        a.prepayments.add(new StartupBook.Prepayment(product, count, value, (int) Math.round(months * DAYS_PER_MONTH)));
        a.prepayReceivedCents += paid;
        DataCenterApi.deposit(level, id, paid);
    }

    /**
     * One day of the running plans: customers that left get the rest of their plan back, the others'
     * day is settled against the income they bring.
     */
    static void settleDay(ServerLevel level, UUID id, StartupBook.Account a) {
        if (a.prepayments.isEmpty()) {
            return;
        }
        HostingCustomers customers = HostingCustomers.get(level);
        for (HostingProduct product : HostingProduct.values()) {
            double prepaid = 0;
            for (StartupBook.Prepayment p : a.prepayments) {
                if (p.product == product) {
                    prepaid += p.customers;
                }
            }
            double now = customers.total(id, product);
            if (prepaid <= 0 || now >= prepaid) {
                continue;
            }
            double left = 1 - now / prepaid;
            for (StartupBook.Prepayment p : a.prepayments) {
                if (p.product == product) {
                    double refund = p.remainingCents * left;
                    a.prepayDebtCents += refund;
                    p.remainingCents -= refund;
                    p.customers *= 1 - left;
                }
            }
        }
        for (Iterator<StartupBook.Prepayment> it = a.prepayments.iterator(); it.hasNext(); ) {
            StartupBook.Prepayment p = it.next();
            double day = p.daysLeft <= 1 ? p.remainingCents : p.remainingCents / p.daysLeft;
            a.prepayDebtCents += day;
            p.remainingCents -= day;
            p.daysLeft--;
            if (p.daysLeft <= 0 || p.remainingCents < 1) {
                a.prepayDebtCents += Math.max(0, p.remainingCents);
                it.remove();
            }
        }
    }

    /** Takes what is settled but not yet paid from the wallet, as far as it goes. */
    static void collect(ServerLevel level, UUID id, StartupBook.Account a) {
        long debt = (long) Math.floor(a.prepayDebtCents);
        if (debt > 0) {
            long take = Math.min(debt, Math.max(0, DataCenterApi.balanceCents(level, id)));
            if (take > 0 && DataCenterApi.withdraw(level, id, take)) {
                a.prepayDebtCents -= take;
            }
        }
    }

    static void setEnabled(StartupBook.Account a, boolean enabled) {
        a.prepayEnabled = enabled;
        if (!enabled) {
            Arrays.fill(a.prepayPending, 0);
        }
    }

    static HostingSnapshot.Prepay snapshot(StartupBook.Account a) {
        double open = a.prepayDebtCents;
        double customers = 0;
        for (StartupBook.Prepayment p : a.prepayments) {
            open += p.remainingCents;
            customers += p.customers;
        }
        return new HostingSnapshot.Prepay(Config.prepayShare() > 0, a.prepayEnabled, (int) Math.round(Config.prepayShare() * 100),
                (int) Math.round(Config.prepayDiscount() * 100), a.prepayReceivedCents, Math.round(open), (int) Math.round(customers));
    }
}
