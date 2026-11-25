package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.hosting.CustomerSegment;
import com.example.datacenter.hosting.HostingCustomers;
import com.example.datacenter.hosting.HostingGrowth;
import com.example.datacenter.hosting.HostingProduct;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Complaints by segment: customers react differently to an outage. Data Center gives every segment an
 * outage sensitivity ({@link CustomerSegment#outage}): company websites 1.0, e-mail 1.5, online shops
 * 2.5, enterprise 3.0, game servers 4.0. Downtime counts in the Ruf and in the cancellations weighted by
 * the average sensitivity of the product's customers, relative to {@link #REFERENCE}.
 */
public final class Sensitivity {
    /** The sensitivity downtime counts at its plain weight (about a typical shared web mix). */
    static final double REFERENCE = 1.5;
    private static final int REFRESH_TICKS = 100;

    private record Key(UUID owner, HostingProduct product) {
    }

    /** Cached factor and the server tick it was computed. */
    private static final Map<Key, double[]> CACHE = new HashMap<>();

    private Sensitivity() {
    }

    /** Factor on the downtime of the account's product (1 for dedicated servers). */
    public static double factor(ServerLevel level, @Nullable UUID owner, @Nullable HostingProduct product) {
        if (product == null) {
            return 1.0;
        }
        int tick = level.getServer().getTickCount();
        double[] cached = CACHE.computeIfAbsent(new Key(ReputationBook.key(owner), product), k -> new double[]{1, Integer.MIN_VALUE});
        if (tick - cached[1] >= REFRESH_TICKS) {
            cached[0] = compute(level, owner, product);
            cached[1] = tick;
        }
        return cached[0];
    }

    private static double compute(ServerLevel level, @Nullable UUID owner, HostingProduct product) {
        double[] counts = HostingCustomers.get(level).segments(owner, product);
        double[] w = HostingGrowth.weights(product, counts, DataCenterApi.currentDate(level));
        CustomerSegment[] segments = CustomerSegment.values();
        double avg = 0;
        for (int s = 0; s < w.length && s < segments.length; s++) {
            avg += w[s] * segments[s].outage;
        }
        return avg <= 0 ? 1.0 : avg / REFERENCE;
    }

    static void clear() {
        CACHE.clear();
    }
}
