package de.f4ked.data_center_addon.hosting.market;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.hosting.HostingCustomers;
import com.example.datacenter.hosting.HostingProduct;
import com.example.datacenter.hosting.HostingReputation;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.server.level.ServerLevel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The hosting market as the Hosting Manager's Market tab shows it: per product the pool of potential
 * customers ({@link MarketPool}), the part the account can reach ({@link MarketModel}) and its own
 * customers.
 */
public final class MarketService {
    private MarketService() {
    }

    public static HostingSnapshot.Market snapshot(ServerLevel level, UUID account) {
        LocalDate date = DataCenterApi.currentDate(level);
        HostingCustomers book = HostingCustomers.get(level);
        MarketBook market = MarketBook.get(level.getServer());
        HostingReputation rep = HostingReputation.get(level, account);
        double reputation = rep.getPoints();
        List<HostingSnapshot.MarketRow> rows = new ArrayList<>();
        for (HostingProduct product : HostingProduct.values()) {
            if (!MarketPool.exists(product, date)) {
                continue;
            }
            MarketBook.Entry entry = market.entry(account, product);
            MarketModel.Reach reach = MarketModel.reach(entry, product, date, reputation, rep.getUnlockedClass());
            rows.add(new HostingSnapshot.MarketRow(product.ordinal(), reach.pool(), reach.potential(), reach.reputation(),
                    reach.awareness(), reach.classes(), book.total(account, product), entry.ordersGap, entry.ordersWord));
        }
        return new HostingSnapshot.Market(MarketPool.internetUsers(date), List.copyOf(rows));
    }
}
