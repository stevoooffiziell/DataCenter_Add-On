package de.f4ked.data_center_addon.hosting.market;

import com.example.datacenter.hosting.CustomerSegment;
import com.example.datacenter.hosting.HostingGrowth;
import com.example.datacenter.hosting.HostingModeRates;
import com.example.datacenter.hosting.HostingProduct;
import de.f4ked.data_center_addon.Config;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The add-on's customer model. Each product has a pool of potential customers ({@link MarketPool}); an
 * account reaches a share of it, its <b>potential</b>:
 * <pre>potential = pool × base share × reputation × awareness</pre>
 * <ul>
 *   <li><b>Base share</b>: {@code 1 / POOL_FACTOR}, so at 0 reputation and fully known the potential
 *       equals Data Center's own customer equilibrium.</li>
 *   <li><b>Reputation</b>: Data Center's reach, ×1 at 0 points up to ×3 at 1000.</li>
 *   <li><b>Awareness</b>: ×0.5 for a product nobody knows the account for, up to ×1. It grows while the
 *       account offers places of the product (63 % of the way in {@link #AWARENESS_UP_MONTHS} game
 *       months) and fades slowly while it offers none.</li>
 * </ul>
 * New customers close the gap between potential and customers and come by word of mouth ({@link #step}).
 */
public final class MarketModel {
    public static final double BASE_SHARE = 1.0 / MarketPool.POOL_FACTOR;
    /** Time constant of awareness growing while places are offered, in game months. */
    static final double AWARENESS_UP_MONTHS = 2.0;
    /** Time constant of awareness fading while no places are offered, in game months. */
    static final double AWARENESS_DOWN_MONTHS = 6.0;
    static final double AWARENESS_FLOOR = 0.5;

    private MarketModel() {
    }

    /**
     * What an account can reach of a product's pool.
     *
     * @param reputation factor from reputation (Data Center's reach)
     * @param awareness  factor from awareness (0.5..1)
     */
    public record Reach(double pool, double reputation, double awareness, double potential) {
        public double share() {
            return pool <= 0 ? 0 : potential / pool;
        }
    }

    public static Reach reach(MarketBook.Entry entry, HostingProduct product, LocalDate date, double reputationPoints) {
        double pool = MarketPool.pool(product, date);
        double rep = HostingModeRates.reachFactor(reputationPoints);
        double aware = AWARENESS_FLOOR + (1 - AWARENESS_FLOOR) * entry.awareness();
        return new Reach(pool, rep, aware, pool * BASE_SHARE * rep * aware);
    }

    /**
     * Called by Data Center's customer book every tick for every product an account sells or has
     * customers of, before the book moves on: awareness grows while places are offered.
     */
    public static MarketBook.Entry observe(MinecraftServer server, @Nullable UUID owner, HostingProduct product,
                                           double customers, double places, double dtMonths) {
        MarketBook book = MarketBook.get(server);
        MarketBook.Entry e = book.entry(owner, product);
        if (Double.isNaN(e.awareness)) {
            // accounts that already sell the product are known for it
            e.awareness = customers > 0 ? 1 : 0;
        } else if (places > 0) {
            e.awareness += (1 - e.awareness) * Math.min(1, dtMonths / AWARENESS_UP_MONTHS);
        } else {
            e.awareness -= e.awareness * Math.min(1, dtMonths / AWARENESS_DOWN_MONTHS);
        }
        e.awareness = MarketBook.clamp01(e.awareness);
        book.setDirty();
        return e;
    }

    // ------------------------------------------------------------------ one tick of the customer book

    /** New customers and cancellations per game month, as Data Center's rack menu shows them. */
    public record Flow(double orders, double cancellations) {
    }

    /**
     * Moves one account's customers of one product on by one tick, in place of Data Center's own step.
     * <ol>
     *   <li>Customers cancel as in Data Center (about 3 % a month, by segment and year).</li>
     *   <li>New customers close the gap to the potential: {@code (potential − customers) × closeRate} a
     *       month.</li>
     *   <li>Word of mouth: every customer brings {@code wordOfMouth} new ones a month, less the closer the
     *       customers are to the potential ({@code × (1 − customers / potential)}, logistic growth).</li>
     *   <li>New customers only take free places. They come in Data Center's segment mix.</li>
     *   <li>When there are fewer places than customers, the excess leaves within days, as in Data Center.</li>
     * </ol>
     *
     * @param counts the account's customers of the product by segment, changed in place
     */
    public static Flow step(MarketBook.Entry entry, HostingProduct product, double[] counts, LocalDate date,
                            double reputationPoints, double places, double dtMonths) {
        CustomerSegment[] segments = CustomerSegment.values();
        double total = 0;
        for (double c : counts) {
            total += c;
        }

        for (int s = 0; s < segments.length && s < counts.length; s++) {
            if (counts[s] > 0) {
                double lost = counts[s] * Math.min(1, HostingGrowth.churn(product, segments[s], date) * dtMonths);
                counts[s] -= lost;
                total -= lost;
            }
        }

        Reach reach = reach(entry, product, date, reputationPoints);
        double gap = Config.closeRate() * Math.max(0, reach.potential() - total);
        double word = reach.potential() <= 0 ? 0 : Config.wordOfMouth() * total * Math.max(0, 1 - total / reach.potential());
        entry.ordersGap = gap;
        entry.ordersWord = word;
        double orders = gap + word;
        double free = Math.max(0, places - total);
        double arriving = Math.min(free, orders * dtMonths);
        if (arriving > 0) {
            double[] mix = HostingGrowth.orderMix(product, date);
            for (int s = 0; s < segments.length && s < counts.length; s++) {
                counts[s] += arriving * mix[s];
            }
            total += arriving;
        }

        leaveOverPlaces(segments, counts, total, places, dtMonths);
        for (int s = 0; s < counts.length; s++) {
            if (counts[s] < 1.0E-9) {
                counts[s] = 0;
            }
        }
        return new Flow(orders, HostingGrowth.churnPerMonth(product, counts, date));
    }

    /** Customers above the places offered leave within days, the more outage-sensitive the faster (as in Data Center). */
    private static void leaveOverPlaces(CustomerSegment[] segments, double[] counts, double total, double places, double dtMonths) {
        if (total <= places + 1.0E-9 || total <= 0) {
            return;
        }
        double excessShare = (total - Math.max(0, places)) / total;
        double days = dtMonths * 730.5 / 24.0;
        for (int s = 0; s < segments.length && s < counts.length; s++) {
            if (counts[s] > 0) {
                double leave = 1 - Math.exp(-days * segments[s].outage / HostingGrowth.EXCESS_LEAVE_DAYS);
                counts[s] -= counts[s] * excessShare * leave;
            }
        }
    }
}
