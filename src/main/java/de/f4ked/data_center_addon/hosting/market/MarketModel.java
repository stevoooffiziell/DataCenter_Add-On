package de.f4ked.data_center_addon.hosting.market;

import com.example.datacenter.hosting.CustomerSegment;
import com.example.datacenter.hosting.HostingGrowth;
import com.example.datacenter.hosting.HostingProduct;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.hosting.reputation.ClassRules;
import de.f4ked.data_center_addon.hosting.reputation.CustomerTier;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The add-on's customer model. Each product has a pool of potential customers ({@link MarketPool}); an
 * account reaches a share of it, its <b>potential</b>:
 * <pre>potential = pool × market events × competition × base share × reputation × awareness × customer classes × price × Ruf</pre>
 * <ul>
 *   <li><b>Competition</b>: the world's fictional competitors ({@link Competitors}).</li>
 *   <li><b>Market events</b>: dotcom boom and crash, Web 2.0 boom, financial crisis ({@link MarketEvents}).</li>
 *   <li><b>Base share</b>: {@code 1 / POOL_FACTOR}, so at 0 reputation and fully known the potential
 *       equals Data Center's own customer equilibrium.</li>
 *   <li><b>Reputation</b>: ×1 at 0 points up to ×2 at 1000, and on to ×2.5 at 2500.</li>
 *   <li><b>Customer classes</b>: every customer class the account has unlocked opens a sub-pool of its
 *       own ({@link CustomerTier#pool}): 15 % of the hobbyist pool each for the lower classes, down to
 *       5 % for the top class. Their customers pay better (they lean to the better paying
 *       segments).</li>
 *   <li><b>Awareness</b>: ×0.5 for a product nobody knows the account for, up to ×1. It grows while the
 *       account offers places of the product (63 % of the way in {@link #AWARENESS_UP_MONTHS} game
 *       months) and fades slowly while it offers none.</li>
 *   <li><b>Price</b>: the account sets each product's price from −30 % to +50 % of the list price. Each
 *       10 % cheaper raises the share by {@link #PRICE_ELASTICITY} × 10 %, each 10 % dearer lowers it as
 *       much. What the customers pay changes with the price.</li>
 * </ul>
 * With several players in one market the potential is shared ({@link SharedPool}).
 * New customers close the gap between potential and customers and come by word of mouth ({@link #step}).
 */
public final class MarketModel {
    public static final double BASE_SHARE = 1.0 / MarketPool.POOL_FACTOR;
    /** Time constant of awareness growing while places are offered, in game months. */
    static final double AWARENESS_UP_MONTHS = 2.0;
    /** Time constant of awareness fading while no places are offered, in game months. */
    static final double AWARENESS_DOWN_MONTHS = 6.0;
    static final double AWARENESS_FLOOR = 0.5;
    /** Reputation factor at 1000 points (×1 at 0). */
    static final double REPUTATION_MAX = 2.0;
    /** What the points above 1000 add to the reputation factor, up to {@link ClassRules#MAX_RENOWN}. */
    static final double REPUTATION_BEYOND = 0.5;
    /** Price steps: 10 % each, from −30 % to +50 %. */
    public static final double PRICE_STEP = 0.10;
    public static final int PRICE_MIN = -3, PRICE_MAX = 5;
    /** How strongly the share answers the price: share × (1 − elasticity × price change). */
    static final double PRICE_ELASTICITY = 1.5;
    /** Share per game month of the customers above the potential that leave (a shrinking market, a lost class). */
    static final double OVER_POTENTIAL_LEAVE = 0.10;

    private MarketModel() {
    }

    /**
     * What an account can reach of a product's pool.
     *
     * @param pool       the pool after the market events
     * @param market     factor of the market events on the pool
     * @param rivals     factor of the fictional competitors
     * @param reputation factor from reputation
     * @param awareness  factor from awareness (0.5..1)
     * @param classes    factor from the customer classes' sub-pools (1 for hobbyists only)
     * @param price      factor from the account's price
     * @param ruf        factor from the account's Ruf (quality of the last 30 days)
     * @param sharing    factor from sharing the pool with other players (1 alone)
     * @param accounts   accounts sharing the pool (1 alone)
     */
    public record Reach(double pool, double market, double rivals, double reputation, double awareness, double classes,
                        double price, double ruf, double sharing, int accounts, double potential) {
        public double share() {
            return pool <= 0 ? 0 : potential / pool;
        }

        Reach shared(SharedPool.Sharing s) {
            return new Reach(pool, market, rivals, reputation, awareness, classes, price, ruf, s.factor(), s.accounts(),
                    potential * s.factor());
        }

        /** Share of the potential that comes from the sub-pools of the higher customer classes. */
        public double premiumShare() {
            return classes <= 0 ? 0 : (classes - 1) / classes;
        }
    }

    /**
     * @param market factor of the market events ({@link MarketEvents#factor})
     * @param rivals factor of the competitors ({@link Competitors#factor})
     * @param ruf    factor of the account's Ruf ({@code Ruf#reachFactor})
     */
    public static Reach reach(MarketBook.Entry entry, HostingProduct product, LocalDate date, double reputationPoints,
                              CustomerTier unlocked, double market, double rivals, double ruf) {
        double pool = MarketPool.pool(product, date) * market;
        double rep = reputationFactor(reputationPoints);
        double aware = AWARENESS_FLOOR + (1 - AWARENESS_FLOOR) * entry.awareness();
        double classes = classFactor(unlocked);
        double price = Config.marketModel() ? demandFactor(entry.priceStep) : 1.0;
        return new Reach(pool, market, rivals, rep, aware, classes, price, ruf, 1.0, 1,
                pool * rivals * BASE_SHARE * rep * aware * classes * price * ruf);
    }

    /**
     * The reach with the pool shared among the accounts in the market ({@link SharedPool}).
     *
     * @param tick the server tick; {@code step} notes the account as active, the Market tab only reads
     */
    public static Reach sharedReach(MarketBook book, MarketBook.Entry entry, HostingProduct product, LocalDate date,
                                    double reputationPoints, CustomerTier unlocked, double market, double rivals, double ruf,
                                    long tick, boolean note) {
        Reach alone = reach(entry, product, date, reputationPoints, unlocked, market, rivals, ruf);
        if (note) {
            SharedPool.note(entry, alone.share(), tick);
        }
        return alone.shared(SharedPool.sharing(book, product, entry, alone.share(), tick));
    }

    /** The price change of a step, e.g. +0.2 for two steps up. */
    public static double priceChange(int step) {
        return step * PRICE_STEP;
    }

    /** What the customers pay relative to the list price. */
    public static double payFactor(int step) {
        return 1 + priceChange(step);
    }

    /** How the price changes the share of the market the account reaches. */
    public static double demandFactor(int step) {
        return Math.max(0.1, 1 - PRICE_ELASTICITY * priceChange(step));
    }

    static double reputationFactor(double points) {
        double beyond = Math.max(0, Math.min(ClassRules.MAX_RENOWN, points) - 1000) / (ClassRules.MAX_RENOWN - 1000);
        return 1 + (REPUTATION_MAX - 1) * Math.max(0, Math.min(1000, points)) / 1000 + REPUTATION_BEYOND * beyond;
    }

    /** The hobbyist pool plus the sub-pools of every class up to {@code unlocked}. */
    public static double classFactor(CustomerTier unlocked) {
        double sum = 0;
        for (CustomerTier t : CustomerTier.values()) {
            if (t.ordinal() <= unlocked.ordinal()) {
                sum += t.pool;
            }
        }
        return sum;
    }

    /**
     * The segment mix of new customers: Data Center's mix of the year, raised by the segment pools
     * ({@link SegmentPools}), for the hobbyist pool; for the classes' sub-pools the same mix weighted by
     * what each segment pays.
     */
    static double[] arrivalMix(HostingProduct product, LocalDate date, double premiumShare) {
        double[] mix = SegmentPools.mix(product, date);
        if (premiumShare <= 0) {
            return mix;
        }
        CustomerSegment[] segments = CustomerSegment.values();
        double[] premium = new double[mix.length];
        double total = 0;
        for (int s = 0; s < mix.length && s < segments.length; s++) {
            premium[s] = mix[s] * segments[s].pay;
            total += premium[s];
        }
        double[] out = new double[mix.length];
        for (int s = 0; s < mix.length; s++) {
            out[s] = (1 - premiumShare) * mix[s] + premiumShare * (total <= 0 ? mix[s] : premium[s] / total);
        }
        return out;
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
     *   <li>Customers cancel as in Data Center (by segment and year), scaled by the quality of the service
     *       ({@link ServiceQuality}): 1 % a month with good service up to 8 % with bad.</li>
     *   <li>New customers close the gap to the potential: {@code (potential − customers − waiting) ×
     *       closeRate} a month.</li>
     *   <li>Word of mouth: every customer brings {@code wordOfMouth} new ones a month, less the closer the
     *       customers are to the potential ({@code × (1 − customers / potential)}, logistic growth).</li>
     *   <li>New customers only take free places, those on the waiting list first. They come in Data
     *       Center's segment mix, the sub-pools' share of them leaning to the better paying segments.</li>
     *   <li>New customers who find no free place go on the waiting list; they give up after
     *       {@code waitDays} in-game days on average.</li>
     *   <li>Customers above the potential (the market shrank, a class was lost) leave at
     *       {@link #OVER_POTENTIAL_LEAVE} of the excess a month.</li>
     *   <li>When there are fewer places than customers, the excess leaves within days, as in Data Center.</li>
     * </ol>
     *
     * @param counts the account's customers of the product by segment, changed in place
     * @param reach  what the account reaches of the product's pool ({@link #sharedReach})
     */
    public static Flow step(MarketBook.Entry entry, HostingProduct product, double[] counts, LocalDate date, Reach reach,
                            double places, double dtMonths) {
        CustomerSegment[] segments = CustomerSegment.values();
        double total = 0;
        for (double c : counts) {
            total += c;
        }

        double quality = ServiceQuality.churnFactor(entry);
        for (int s = 0; s < segments.length && s < counts.length; s++) {
            if (counts[s] > 0) {
                double lost = counts[s] * Math.min(1, HostingGrowth.churn(product, segments[s], date) * quality * dtMonths);
                counts[s] -= lost;
                total -= lost;
            }
        }

        if (total > reach.potential() && total > 0) {
            double leaveShare = (total - reach.potential()) * Math.min(1, OVER_POTENTIAL_LEAVE * dtMonths) / total;
            for (int s = 0; s < counts.length; s++) {
                counts[s] -= counts[s] * leaveShare;
            }
            total -= total * leaveShare;
        }
        double interested = total + entry.waiting;
        double gap = Config.closeRate() * Math.max(0, reach.potential() - interested);
        double word = reach.potential() <= 0 ? 0
                : Config.wordOfMouth() * total * Math.max(0, 1 - interested / reach.potential());
        entry.ordersGap = gap;
        entry.ordersWord = word;
        double orders = gap + word;
        double free = Math.max(0, places - total);
        double fromList = Math.min(free, entry.waiting);
        double newcomers = orders * dtMonths;
        double placed = Math.min(free - fromList, newcomers);
        double arriving = fromList + placed;
        entry.waiting -= fromList;
        double waitDays = Config.waitDays();
        if (waitDays > 0) {
            double days = dtMonths * 730.5 / 24.0;
            entry.waiting += newcomers - placed;
            entry.waiting -= entry.waiting * Math.min(1, days / waitDays);
        } else {
            entry.waiting = 0;
        }
        if (entry.waiting < 1.0E-6) {
            entry.waiting = 0;
        }
        if (arriving > 0) {
            double[] mix = arrivalMix(product, date, reach.premiumShare());
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
        return new Flow(orders, HostingGrowth.churnPerMonth(product, counts, date) * quality);
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
