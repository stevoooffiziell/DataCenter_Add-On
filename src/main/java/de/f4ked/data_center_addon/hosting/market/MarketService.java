package de.f4ked.data_center_addon.hosting.market;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.hosting.HostingCustomers;
import com.example.datacenter.hosting.HostingProduct;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.server.level.ServerLevel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The hosting market as the Hosting Manager's Market tab shows it: per product the pool of potential
 * customers ({@link MarketPool}) and the account's own customers in it.
 */
public final class MarketService {
    private MarketService() {
    }

    public static HostingSnapshot.Market snapshot(ServerLevel level, UUID account) {
        LocalDate date = DataCenterApi.currentDate(level);
        HostingCustomers book = HostingCustomers.get(level);
        List<HostingSnapshot.MarketRow> rows = new ArrayList<>();
        for (HostingProduct product : HostingProduct.values()) {
            if (!MarketPool.exists(product, date)) {
                continue;
            }
            rows.add(new HostingSnapshot.MarketRow(product.ordinal(), MarketPool.pool(product, date), book.total(account, product)));
        }
        return new HostingSnapshot.Market(MarketPool.internetUsers(date), List.copyOf(rows));
    }
}
