package de.f4ked.data_center_addon.hosting.market;

import com.example.datacenter.hosting.HostingModeRates;
import com.example.datacenter.hosting.HostingProduct;
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
    public static void observe(MinecraftServer server, @Nullable UUID owner, HostingProduct product, double customers,
                               double places, double dtMonths) {
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
    }
}
