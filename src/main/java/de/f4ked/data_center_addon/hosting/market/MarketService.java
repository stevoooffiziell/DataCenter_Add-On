package de.f4ked.data_center_addon.hosting.market;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.hosting.HostingCustomers;
import com.example.datacenter.hosting.HostingProduct;
import com.example.datacenter.hosting.HostingReputation;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The hosting market as the Hosting Manager's Market tab shows it: per product the pool of potential
 * customers ({@link MarketPool}), the part the account can reach ({@link MarketModel}), its own
 * customers and the quality of its service ({@link ServiceQuality}, sampled once a second).
 */
@EventBusSubscriber(modid = Data_center_addon.MODID)
public final class MarketService {
    private static final int SAMPLE_TICKS = 20;

    private MarketService() {
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SAMPLE_TICKS != 0) {
            return;
        }
        ServiceQuality.flush(server, SAMPLE_TICKS);
    }

    /**
     * What a customer of the account pays relative to the list price (its price setting). 1 when the
     * market model is off: the price only exists together with the demand it costs.
     */
    public static double priceFactor(ServerLevel level, @Nullable UUID owner, @Nullable HostingProduct product) {
        if (product == null || !Config.marketModel()) {
            return 1.0;
        }
        return MarketModel.payFactor(MarketBook.get(level.getServer()).entry(owner, product).priceStep);
    }

    /** Changes the account's price of a product by {@code delta} steps, from the Market tab. */
    public static void changePrice(ServerLevel level, UUID account, int product, int delta) {
        HostingProduct[] products = HostingProduct.values();
        if (product < 0 || product >= products.length || !Config.marketModel()) {
            return;
        }
        MarketBook book = MarketBook.get(level.getServer());
        MarketBook.Entry e = book.entry(account, products[product]);
        e.priceStep = Math.max(MarketModel.PRICE_MIN, Math.min(MarketModel.PRICE_MAX, e.priceStep + Integer.signum(delta)));
        book.setDirty();
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ServiceQuality.clear();
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
            MarketModel.Reach reach = MarketModel.reach(entry, product, date, reputation, rep.getUnlockedClass(),
                    MarketEvents.factor(level, product, date));
            double customers = book.total(account, product);
            HostingSnapshot.Quality quality = new HostingSnapshot.Quality(ServiceQuality.churnTarget(entry),
                    nanToMinus(entry.down), nanToMinus(entry.overload), nanToMinus(entry.throttle));
            rows.add(new HostingSnapshot.MarketRow(product.ordinal(), reach.pool(), reach.market(), reach.potential(),
                    reach.reputation(), reach.awareness(), reach.classes(), customers, entry.waiting(), entry.ordersGap,
                    entry.ordersWord, quality, boosts(product, date), Config.marketModel() ? entry.priceStep() : 0,
                    reach.price()));
        }
        return new HostingSnapshot.Market(Config.marketModel(), MarketPool.internetUsers(date),
                MarketEvents.phase(level, date).ordinal(), MarketEvents.mood(level, date) * MarketEvents.web20(date),
                List.copyOf(rows));
    }

    private static List<HostingSnapshot.SegmentBoost> boosts(HostingProduct product, LocalDate date) {
        List<HostingSnapshot.SegmentBoost> out = new ArrayList<>();
        for (SegmentPools.Boost b : SegmentPools.boosts(product, date)) {
            out.add(new HostingSnapshot.SegmentBoost(b.segment().ordinal(), b.factor(), b.latest() == null ? "" : b.latest().name(),
                    b.latest() == null ? 0 : b.latest().date().getYear()));
        }
        return List.copyOf(out);
    }

    /** -1 for "not measured yet". */
    private static double nanToMinus(double v) {
        return Double.isNaN(v) ? -1 : v;
    }
}
