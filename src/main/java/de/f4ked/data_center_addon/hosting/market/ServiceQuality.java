package de.f4ked.data_center_addon.hosting.market;

import com.example.datacenter.hosting.HostingProduct;
import com.example.datacenter.mining.MiningEra;
import de.f4ked.data_center_addon.Config;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How well each account serves the customers of each product, and how many of them cancel because of it.
 * <p>
 * Every hosting rack reports its service of the tick to Data Center's reputation ({@code recordService});
 * the add-on reads the same figures ({@code HostingRackBlockEntityMixin}):
 * <ul>
 *   <li><b>Downtime</b>: share of the rack's customers whose server did not run (no power, crashed).</li>
 *   <li><b>Overload</b>: how far the servers are over their real capacity (Data Center's complaints).</li>
 *   <li><b>Throttling</b>: share of the time the rack ran throttled by heat, an attack or a network too
 *       slow for its customers.</li>
 * </ul>
 * Each is averaged over about {@link #AVERAGE_DAYS} in-game days. Together they give a quality from 0
 * (bad) to 1 (good) that sets the monthly cancellations between {@code churnGood} and {@code churnBad}.
 */
public final class ServiceQuality {
    /** Time constant of the averages, in in-game days. */
    static final double AVERAGE_DAYS = 3.0;
    /** Data Center's typical cancellations per month; the base the quality factor scales. */
    static final double NOMINAL_CHURN = 0.03;
    /** Weight of downtime, overload and throttling in the badness. */
    static final double W_DOWN = 1.0, W_OVERLOAD = 0.6, W_THROTTLE = 0.4;

    /** Reports of the current sample: earning, down, overload, throttled earning per account and product. */
    private static final Map<Key, double[]> SAMPLE = new HashMap<>();

    private record Key(UUID owner, HostingProduct product) {
    }

    private ServiceQuality() {
    }

    /** One rack's service of one tick, as it reports it to Data Center's reputation. */
    public static void report(@Nullable UUID owner, @Nullable HostingProduct product, double earning, boolean throttled,
                              double down, double overload) {
        if (product == null || earning + down <= 0) {
            return;
        }
        double[] s = SAMPLE.computeIfAbsent(new Key(MarketBook.key(owner), product), k -> new double[4]);
        s[0] += Math.max(0, earning);
        s[1] += Math.max(0, down);
        s[2] += Math.max(0, Math.min(earning, overload));
        s[3] += throttled ? Math.max(0, earning) : 0;
    }

    /** Folds the reports of the last {@code ticks} ticks into the averages. */
    static void flush(MinecraftServer server, int ticks) {
        if (SAMPLE.isEmpty()) {
            return;
        }
        MarketBook book = MarketBook.get(server);
        double ticksPerDay = 24.0 / MiningEra.fictionalHoursPerTick();
        double alpha = Math.min(1, ticks / (AVERAGE_DAYS * ticksPerDay));
        double alpha30 = Math.min(1, ticks / (30 * ticksPerDay));
        for (Map.Entry<Key, double[]> e : SAMPLE.entrySet()) {
            double[] s = e.getValue();
            double served = s[0] + s[1];
            if (served <= 0) {
                continue;
            }
            MarketBook.Entry entry = book.entry(e.getKey().owner, e.getKey().product);
            entry.down = average(entry.down, s[1] / served, alpha);
            entry.overload = average(entry.overload, s[0] <= 0 ? 0 : s[2] / s[0], alpha);
            entry.throttle = average(entry.throttle, s[0] <= 0 ? 0 : s[3] / s[0], alpha);
            entry.down30 = average(entry.down30, s[1] / served, alpha30);
            entry.overload30 = average(entry.overload30, s[0] <= 0 ? 0 : s[2] / s[0], alpha30);
            entry.throttle30 = average(entry.throttle30, s[0] <= 0 ? 0 : s[3] / s[0], alpha30);
        }
        SAMPLE.clear();
        book.setDirty();
    }

    static void clear() {
        SAMPLE.clear();
    }

    private static double average(double old, double sample, double alpha) {
        return Double.isNaN(old) ? sample : old + (sample - old) * alpha;
    }

    /** Badness 0..1 from the averages; NaN before the first report (Data Center's usual churn applies). */
    public static double badness(MarketBook.Entry e) {
        if (Double.isNaN(e.down)) {
            return Double.NaN;
        }
        return MarketBook.clamp01(W_DOWN * e.down + W_OVERLOAD * e.overload + W_THROTTLE * e.throttle);
    }

    /**
     * The Ruf of the product's own service over about 30 days, with the Ruf's weights (5 × downtime,
     * ½ overload, ½ throttling); NaN before the first report.
     */
    public static double productRuf(MarketBook.Entry e) {
        if (Double.isNaN(e.down30)) {
            return Double.NaN;
        }
        return MarketBook.clamp01(1 - 5 * e.down30 - 0.5 * e.overload30 - 0.5 * e.throttle30);
    }

    /** Cancellations per month the quality aims at: churnGood at badness 0 up to churnBad at 1. */
    public static double churnTarget(MarketBook.Entry e) {
        double bad = badness(e);
        if (Double.isNaN(bad)) {
            return NOMINAL_CHURN;
        }
        return Config.churnGood() + (Config.churnBad() - Config.churnGood()) * bad;
    }

    /** Factor on Data Center's cancellations of every segment. */
    public static double churnFactor(MarketBook.Entry e) {
        return churnTarget(e) / NOMINAL_CHURN;
    }
}
