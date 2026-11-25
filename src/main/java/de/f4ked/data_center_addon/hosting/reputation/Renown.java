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
 *   <li>more customers: {@link #PER_CUSTOMER} for every customer above the most it ever had,</li>
 *   <li>milestones,</li>
 * </ul>
 * and fall through failed or cancelled orders. Renown sets the share of the markets the account reaches.
 * Every change is booked by its source, for the Reputation tab.
 */
public final class Renown {
    /** Renown for every customer above the account's high-water mark (one point per 20 customers). */
    static final double PER_CUSTOMER = 0.05;

    /** Where renown comes from; the name gives {@code data_center_addon.reputation.source.<name>}. */
    public enum Source {
        ORDERS, CUSTOMERS, MILESTONES, PENALTIES;

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

    /** Customers above the account's high-water mark bring renown (checked once a second). */
    static void customers(ServerLevel level, UUID id, ReputationBook.Account a) {
        HostingCustomers book = HostingCustomers.get(level);
        double total = 0;
        for (HostingProduct product : HostingProduct.values()) {
            total += book.total(id, product);
        }
        if (Double.isNaN(a.customerMark)) {
            a.customerMark = total;
        } else if (total > a.customerMark + 1) {
            add(level, id, (total - a.customerMark) * PER_CUSTOMER, Source.CUSTOMERS);
            a.customerMark = total;
        }
    }
}
