package de.f4ked.data_center_addon.hosting;

import de.f4ked.data_center_addon.hosting.contract.ContractService;
import de.f4ked.data_center_addon.hosting.contract.DeliveryService;
import de.f4ked.data_center_addon.hosting.startup.Milestone;
import de.f4ked.data_center_addon.hosting.startup.StartupService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Hosting Manager shows: the hosting racks reachable over its network, the customer
 * segments on them, the account's contracts, delivery orders and founding phase, and the totals.
 * Built on the server ({@link HostingManagerService}), sent to the player who has the manager open.
 * Every record carries its own {@code STREAM_CODEC}.
 *
 * @param connected    whether a cable is plugged into the manager
 * @param foreignRacks racks in the network the player may not control (not listed)
 * @param network      state of the compute network the manager is plugged into
 * @param market       the hosting market of the viewer's account (Market tab)
 * @param standing     the add-on's reputation of the viewer's account (Reputation tab)
 * @param ticksPerDay  ticks in one in-game day (depends on Data Center's calendar pace)
 */
public record HostingSnapshot(boolean connected, int reputation, int customerClass, int foreignRacks,
                              Network network, List<Rack> racks, List<Segment> segments, List<Contract> contracts,
                              List<Delivery> deliveries, Startup startup, Market market, Standing standing,
                              double ticksPerDay) {

    public static final HostingSnapshot EMPTY = new HostingSnapshot(false, 0, 0, 0, Network.NONE, List.of(), List.of(), List.of(),
            List.of(), Startup.NONE, Market.NONE, Standing.NONE, 1000);

    /** Ticks in one real hour (the unit of the Hosting Rack's own "Rate: $/h"). */
    public static final double TICKS_PER_HOUR = 20.0 * 3600.0;

    // ------------------------------------------------------------------ totals

    public double totalCustomers() {
        return racks.stream().mapToDouble(Rack::customers).sum();
    }

    /** Income per in-game day of the racks. */
    public long totalCentsPerDay() {
        return racks.stream().mapToLong(Rack::centsPerDay).sum();
    }

    /** What the running contracts pay per in-game day (paid for every day their places are kept free). */
    public long contractCentsPerDay() {
        return activeContracts().stream().mapToLong(Contract::centsPerDay).sum();
    }

    /** Income per in-game day of the racks plus the running contracts. */
    public long incomeCentsPerDay() {
        return totalCentsPerDay() + contractCentsPerDay();
    }

    /** What the running start bonus pays per in-game day on top of the racks' income (0 when none runs). */
    public long startBonusCentsPerDay() {
        return startup.started() && !startup.veteran() ? Math.round(totalCentsPerDay() * startup.bonusFactor()) : 0;
    }

    /** An amount per in-game day as an amount per real hour. */
    public long perHour(long centsPerDay) {
        return ticksPerDay <= 0 ? 0 : Math.round(centsPerDay * TICKS_PER_HOUR / ticksPerDay);
    }

    /** Racks of this snapshot that earn nothing because of the control panel licence. */
    public int licenceBoundRacks() {
        return (int) racks.stream().filter(Rack::licenceBound).count();
    }

    public List<Contract> activeContracts() {
        return contracts.stream().filter(c -> !c.offer).toList();
    }

    public List<Contract> offers() {
        return contracts.stream().filter(Contract::offer).toList();
    }

    public List<Delivery> runningDeliveries() {
        return deliveries.stream().filter(d -> !d.offer).toList();
    }

    public List<Delivery> deliveryOffers() {
        return deliveries.stream().filter(Delivery::offer).toList();
    }

    // ------------------------------------------------------------------ racks and network

    /**
     * The compute network: what it delivers of what it could, and what slows it down.
     *
     * @param capacity          compute per tick it can pass ({@link Long#MAX_VALUE}: not limited by cables)
     * @param delivered         compute per tick it passes now
     * @param overlongRuns      cable runs longer than their cable's reach
     * @param unpoweredSwitches switches without power (each cuts the network)
     */
    public record Network(long capacity, long delivered, int overlongRuns, int unpoweredSwitches) {
        public static final Network NONE = new Network(0, 0, 0, 0);

        public static final StreamCodec<FriendlyByteBuf, Network> STREAM_CODEC = StreamCodec.of(
                (buf, n) -> {
                    buf.writeVarLong(n.capacity);
                    buf.writeVarLong(n.delivered);
                    buf.writeVarInt(n.overlongRuns);
                    buf.writeVarInt(n.unpoweredSwitches);
                },
                buf -> new Network(buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt()));
    }

    /**
     * One hosting rack.
     *
     * @param product      {@code HostingProduct} ordinal, -1 = dedicated servers
     * @param customers    customers on this rack
     * @param capacity     customers its hardware can serve (places offered = capacity × fill)
     * @param centsPerDay  income per in-game day
     * @param status       {@code HostingRackBlockEntity.MODE_*}
     * @param fill         target fill in percent (shared hosting and VPS)
     * @param licenceBound shared hosting rack with customers that earns nothing: on every server the
     *                     control panel licence ($30 a month) is higher than what its customers pay
     */
    public record Rack(BlockPos pos, int product, double customers, double capacity, long centsPerDay, int status, int fill,
                       boolean licenceBound) {
        public static final StreamCodec<FriendlyByteBuf, Rack> STREAM_CODEC = StreamCodec.of(
                (buf, r) -> {
                    buf.writeBlockPos(r.pos);
                    buf.writeVarInt(r.product + 1);
                    buf.writeDouble(r.customers);
                    buf.writeDouble(r.capacity);
                    buf.writeVarLong(r.centsPerDay);
                    buf.writeVarInt(r.status);
                    buf.writeVarInt(r.fill);
                    buf.writeBoolean(r.licenceBound);
                },
                buf -> new Rack(buf.readBlockPos(), buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(), buf.readVarLong(),
                        buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
    }

    /** Customers of one {@code CustomerSegment} on the racks of this network, and their share of the income. */
    public record Segment(int segment, double customers, long centsPerDay) {
        public static final StreamCodec<FriendlyByteBuf, Segment> STREAM_CODEC = StreamCodec.of(
                (buf, g) -> {
                    buf.writeVarInt(g.segment);
                    buf.writeDouble(g.customers);
                    buf.writeVarLong(g.centsPerDay);
                },
                buf -> new Segment(buf.readVarInt(), buf.readDouble(), buf.readVarLong()));
    }

    // ------------------------------------------------------------------ contracts and deliveries

    /**
     * A running hosting contract or an offer ({@link ContractService}).
     *
     * @param client    index of the client's name
     * @param product   {@code HostingProduct} ordinal
     * @param accounts  places the client wants kept free
     * @param free      places of the product free now (capacity minus customers of all the account's racks)
     * @param required  places all running contracts of the product need (for an offer: with it)
     * @param metToday  share of today the places were free (running contracts)
     * @param expiresIn days an offer stays open
     * @param urgent    emergency order: short, well paid, open for one day
     * @param mini      small hobbyist order: no penalty, no reputation lost
     */
    public record Contract(int id, boolean offer, int client, int product, int accounts, int days, int daysDone, int strikes,
                           long centsPerDay, long bonusCents, long penaltyCents, int reputation, double free, int required,
                           float metToday, int expiresIn, boolean urgent, boolean mini) {
        public boolean covered() {
            return free >= required;
        }

        /** Reputation a failed or cancelled contract costs (mini contracts: none). */
        public int reputationLoss() {
            return mini ? 0 : reputation * 2;
        }

        public static final StreamCodec<FriendlyByteBuf, Contract> STREAM_CODEC = StreamCodec.of(
                (buf, c) -> {
                    buf.writeVarInt(c.id);
                    buf.writeBoolean(c.offer);
                    buf.writeVarInt(c.client);
                    buf.writeVarInt(c.product);
                    buf.writeVarInt(c.accounts);
                    buf.writeVarInt(c.days);
                    buf.writeVarInt(c.daysDone);
                    buf.writeVarInt(c.strikes);
                    buf.writeVarLong(c.centsPerDay);
                    buf.writeVarLong(c.bonusCents);
                    buf.writeVarLong(c.penaltyCents);
                    buf.writeVarInt(c.reputation);
                    buf.writeDouble(c.free);
                    buf.writeVarInt(c.required);
                    buf.writeFloat(c.metToday);
                    buf.writeVarInt(c.expiresIn);
                    buf.writeBoolean(c.urgent);
                    buf.writeBoolean(c.mini);
                },
                buf -> new Contract(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                        buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readBoolean(),
                        buf.readBoolean()));
    }

    /**
     * A delivery order or an offer ({@link DeliveryService}).
     *
     * @param days     days the client gives from accepting
     * @param daysLeft running: days left to deliver (1 = today is the last); offer: days it stays open
     */
    public record Delivery(int id, boolean offer, int client, List<Goal> goals, long payCents, int reputation, int days,
                           int daysLeft) {
        public int delivered() {
            return goals.stream().mapToInt(Goal::delivered).sum();
        }

        public int total() {
            return goals.stream().mapToInt(Goal::count).sum();
        }

        /** Whether the player carries something this delivery still needs. */
        public boolean canDeliver() {
            return goals.stream().anyMatch(g -> g.delivered < g.count && g.inInventory > 0);
        }

        public static final StreamCodec<FriendlyByteBuf, Delivery> STREAM_CODEC = StreamCodec.of(
                (buf, d) -> {
                    buf.writeVarInt(d.id);
                    buf.writeBoolean(d.offer);
                    buf.writeVarInt(d.client);
                    writeList(buf, d.goals, Goal.STREAM_CODEC);
                    buf.writeVarLong(d.payCents);
                    buf.writeVarInt(d.reputation);
                    buf.writeVarInt(d.days);
                    buf.writeVarInt(d.daysLeft);
                },
                buf -> new Delivery(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), readList(buf, Goal.STREAM_CODEC, 8),
                        buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
    }

    /**
     * One goal of a delivery.
     *
     * @param kind        {@code DeliveryService.GoalKind} ordinal
     * @param item        the item wanted (ITEM) or shown as icon
     * @param inInventory matching items in the viewing player's inventory
     */
    public record Goal(int kind, ResourceLocation item, int count, int delivered, int inInventory) {
        public static final StreamCodec<FriendlyByteBuf, Goal> STREAM_CODEC = StreamCodec.of(
                (buf, g) -> {
                    buf.writeVarInt(g.kind);
                    buf.writeResourceLocation(g.item);
                    buf.writeVarInt(g.count);
                    buf.writeVarInt(g.delivered);
                    buf.writeVarInt(g.inInventory);
                },
                buf -> new Goal(buf.readVarInt(), buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
    }

    // ------------------------------------------------------------------ founding phase

    /**
     * The founding phase of the viewer's company ({@link StartupService}).
     *
     * @param started          the first rack income was seen (the start bonus and grant start with it)
     * @param veteran          an established company when first seen: no start bonus, no grant
     * @param bonusFactor      extra share of rack income paid now (0 = no bonus)
     * @param bonusMode        {@code Config.StartBonusMode} ordinal
     * @param bonusDaysLeft    TIME: days until the bonus has faded out
     * @param bonusRevenueLeft REVENUE: rack income in cents until the bonus ends
     * @param grantOfferCents  founding grant paid with the first rack income (0 = none)
     * @param loanOfferCents   founding loan on offer (0 = none)
     * @param loanGraceDays    days until the first instalment (0 = repaying)
     * @param milestones       {@link Milestone#bit()} of the milestones reached
     * @param milestoneRewards reward in cents of every milestone, in {@link Milestone} order
     */
    public record Startup(boolean started, boolean veteran, double bonusFactor, int bonusMode, int bonusDaysLeft,
                          long bonusRevenueLeft, long bonusPaidCents, long grantCents, long grantOfferCents, long feesPaidCents,
                          long loanOfferCents, boolean loanTaken, long loanOutstandingCents, int loanGraceDays,
                          long loanInstalmentCents, long milestones, List<Long> milestoneRewards, List<Ticket> tickets,
                          Prepay prepay) {
        public static final Startup NONE = new Startup(false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, List.of(),
                List.of(), Prepay.NONE);

        public boolean reached(Milestone milestone) {
            return (milestones & milestone.bit()) != 0;
        }

        private static final StreamCodec<FriendlyByteBuf, Long> REWARD = StreamCodec.of(
                (buf, v) -> buf.writeVarLong(v), FriendlyByteBuf::readVarLong);

        public static final StreamCodec<FriendlyByteBuf, Startup> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeBoolean(s.started);
                    buf.writeBoolean(s.veteran);
                    buf.writeDouble(s.bonusFactor);
                    buf.writeVarInt(s.bonusMode);
                    buf.writeVarInt(s.bonusDaysLeft);
                    buf.writeVarLong(s.bonusRevenueLeft);
                    buf.writeVarLong(s.bonusPaidCents);
                    buf.writeVarLong(s.grantCents);
                    buf.writeVarLong(s.grantOfferCents);
                    buf.writeVarLong(s.feesPaidCents);
                    buf.writeVarLong(s.loanOfferCents);
                    buf.writeBoolean(s.loanTaken);
                    buf.writeVarLong(s.loanOutstandingCents);
                    buf.writeVarInt(s.loanGraceDays);
                    buf.writeVarLong(s.loanInstalmentCents);
                    buf.writeLong(s.milestones);
                    writeList(buf, s.milestoneRewards, REWARD);
                    writeList(buf, s.tickets, Ticket.STREAM_CODEC);
                    Prepay.STREAM_CODEC.encode(buf, s.prepay);
                },
                buf -> new Startup(buf.readBoolean(), buf.readBoolean(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                        buf.readBoolean(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readLong(),
                        readList(buf, REWARD, 64), readList(buf, Ticket.STREAM_CODEC, 16), Prepay.STREAM_CODEC.decode(buf)));
    }

    /**
     * Annual plans paid in advance.
     *
     * @param available prepayment is turned on in the config
     * @param enabled   the account offers annual plans to new customers
     * @param openCents value not yet settled against the income (plans running plus what is still to be taken)
     */
    public record Prepay(boolean available, boolean enabled, int sharePercent, int discountPercent, long receivedCents,
                         long openCents, int customers) {
        public static final Prepay NONE = new Prepay(false, false, 0, 0, 0, 0, 0);

        public static final StreamCodec<FriendlyByteBuf, Prepay> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.available);
                    buf.writeBoolean(p.enabled);
                    buf.writeVarInt(p.sharePercent);
                    buf.writeVarInt(p.discountPercent);
                    buf.writeVarLong(p.receivedCents);
                    buf.writeVarLong(p.openCents);
                    buf.writeVarInt(p.customers);
                },
                buf -> new Prepay(buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(),
                        buf.readVarLong(), buf.readVarInt()));
    }

    /** A service ticket: right-click the rack at {@code pos} before it runs out. */
    public record Ticket(int id, BlockPos pos, int kind, long rewardCents, int daysLeft) {
        public static final StreamCodec<FriendlyByteBuf, Ticket> STREAM_CODEC = StreamCodec.of(
                (buf, t) -> {
                    buf.writeVarInt(t.id);
                    buf.writeBlockPos(t.pos);
                    buf.writeVarInt(t.kind);
                    buf.writeVarLong(t.rewardCents);
                    buf.writeVarInt(t.daysLeft);
                },
                buf -> new Ticket(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt()));
    }

    // ------------------------------------------------------------------ market

    /**
     * The hosting market ({@code MarketService}).
     *
     * @param model         whether the add-on's market model runs (prices can only be set then)
     * @param shared        whether the accounts share the pools (multiplayer)
     * @param internetUsers internet users worldwide in millions
     * @param phase         {@code MarketEvents.Phase} ordinal
     * @param mood          factor of the market events on the pools (colocation excepted)
     * @param rows          one row per product that can be sold today
     * @param rivals        the world's fictional competitors, strongest first
     */
    public record Market(boolean model, boolean shared, double internetUsers, int phase, double mood, List<MarketRow> rows,
                         List<Rival> rivals) {
        public static final Market NONE = new Market(false, false, 0, 0, 1, List.of(), List.of());

        /** New customers of all products waiting for a free place. */
        public double waiting() {
            return rows.stream().mapToDouble(MarketRow::waiting).sum();
        }

        /** The row of a {@code HostingProduct} ordinal, or null. */
        public MarketRow row(int product) {
            for (MarketRow r : rows) {
                if (r.product == product) {
                    return r;
                }
            }
            return null;
        }

        public static final StreamCodec<FriendlyByteBuf, Market> STREAM_CODEC = StreamCodec.of(
                (buf, m) -> {
                    buf.writeBoolean(m.model);
                    buf.writeBoolean(m.shared);
                    buf.writeDouble(m.internetUsers);
                    buf.writeVarInt(m.phase);
                    buf.writeDouble(m.mood);
                    writeList(buf, m.rows, MarketRow.STREAM_CODEC);
                    writeList(buf, m.rivals, Rival.STREAM_CODEC);
                },
                buf -> new Market(buf.readBoolean(), buf.readBoolean(), buf.readDouble(), buf.readVarInt(), buf.readDouble(),
                        readList(buf, MarketRow.STREAM_CODEC, 16), readList(buf, Rival.STREAM_CODEC, 16)));
    }

    /**
     * A fictional competitor ({@code Competitors}).
     *
     * @param products bit per {@code HostingProduct} ordinal it sells
     * @param strength its share of the markets it sells in
     */
    public record Rival(String name, int products, double strength) {
        public static final StreamCodec<FriendlyByteBuf, Rival> STREAM_CODEC = StreamCodec.of(
                (buf, r) -> {
                    buf.writeUtf(r.name, 64);
                    buf.writeVarInt(r.products);
                    buf.writeDouble(r.strength);
                },
                buf -> new Rival(buf.readUtf(64), buf.readVarInt(), buf.readDouble()));
    }

    /**
     * The market of one product.
     *
     * @param product   {@code HostingProduct} ordinal
     * @param pool        potential customers in the whole market (after the market events)
     * @param market      factor of the market events on the pool
     * @param potential   the part of the pool the account can reach
     * @param repFactor   factor of the reach from reputation
     * @param awareFactor factor of the reach from awareness
     * @param classFactor factor of the reach from the customer classes' sub-pools
     * @param customers   the account's customers of the product
     * @param waiting     new customers waiting for a free place
     * @param ordersGap   new customers per month closing the gap to the potential
     * @param ordersWord  new customers per month by word of mouth
     * @param quality     the quality of the service and the cancellations it leads to
     * @param boosts      segments of the product raised by launches in the world
     * @param priceStep   the account's price in 10 % steps from the list price
     * @param priceFactor factor of the reach from the price
     * @param rufFactor   factor of the reach from the account's Ruf
     * @param competition summed market share of the competitors that sell the product
     * @param rivalFactor factor of the reach from the competitors
     * @param sharing     factor of the reach from sharing the pool with other players
     * @param accounts    accounts sharing the pool (1 alone)
     * @param nextClass   {@code CustomerClass} ordinal of the next class to unlock (-1: the highest is reached)
     * @param nextGain    reachable customers the next class would add (its reputation and sub-pool)
     */
    public record MarketRow(int product, double pool, double market, double potential, double repFactor, double awareFactor,
                            double classFactor, double customers, double waiting, double ordersGap, double ordersWord,
                            Quality quality, List<SegmentBoost> boosts, int priceStep, double priceFactor, double rufFactor,
                            double competition,
                            double rivalFactor, double sharing, int accounts, int nextClass, double nextGain) {
        public double orders() {
            return ordersGap + ordersWord;
        }

        public static final StreamCodec<FriendlyByteBuf, MarketRow> STREAM_CODEC = StreamCodec.of(
                (buf, r) -> {
                    buf.writeVarInt(r.product);
                    buf.writeDouble(r.pool);
                    buf.writeDouble(r.market);
                    buf.writeDouble(r.potential);
                    buf.writeDouble(r.repFactor);
                    buf.writeDouble(r.awareFactor);
                    buf.writeDouble(r.classFactor);
                    buf.writeDouble(r.customers);
                    buf.writeDouble(r.waiting);
                    buf.writeDouble(r.ordersGap);
                    buf.writeDouble(r.ordersWord);
                    Quality.STREAM_CODEC.encode(buf, r.quality);
                    writeList(buf, r.boosts, SegmentBoost.STREAM_CODEC);
                    buf.writeVarInt(r.priceStep);
                    buf.writeDouble(r.priceFactor);
                    buf.writeDouble(r.rufFactor);
                    buf.writeDouble(r.competition);
                    buf.writeDouble(r.rivalFactor);
                    buf.writeDouble(r.sharing);
                    buf.writeVarInt(r.accounts);
                    buf.writeVarInt(r.nextClass + 1);
                    buf.writeDouble(r.nextGain);
                },
                buf -> new MarketRow(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        Quality.STREAM_CODEC.decode(buf), readList(buf, SegmentBoost.STREAM_CODEC, 16), buf.readVarInt(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(),
                        buf.readVarInt() - 1, buf.readDouble()));
    }

    /**
     * A customer segment raised by launches in the world ({@code SegmentPools}).
     *
     * @param segment {@code CustomerSegment} ordinal
     * @param factor  how much its pool is raised
     * @param latest  the latest launch ("" if none)
     * @param year    the year of the latest launch
     */
    public record SegmentBoost(int segment, double factor, String latest, int year) {
        public static final StreamCodec<FriendlyByteBuf, SegmentBoost> STREAM_CODEC = StreamCodec.of(
                (buf, b) -> {
                    buf.writeVarInt(b.segment);
                    buf.writeDouble(b.factor);
                    buf.writeUtf(b.latest, 64);
                    buf.writeVarInt(b.year);
                },
                buf -> new SegmentBoost(buf.readVarInt(), buf.readDouble(), buf.readUtf(64), buf.readVarInt()));
    }

    /**
     * The service of one product ({@code ServiceQuality}); the shares are -1 before the first measurement.
     *
     * @param churn    cancellations per month the service leads to (before segments and events)
     * @param down     share of customers whose server did not run
     * @param overload how far the servers run over their real capacity
     * @param throttle share of the time the racks ran throttled (heat, attack, slow network)
     */
    public record Quality(double churn, double down, double overload, double throttle) {
        public boolean measured() {
            return down >= 0;
        }

        public static final StreamCodec<FriendlyByteBuf, Quality> STREAM_CODEC = StreamCodec.of(
                (buf, q) -> {
                    buf.writeDouble(q.churn);
                    buf.writeDouble(q.down);
                    buf.writeDouble(q.overload);
                    buf.writeDouble(q.throttle);
                },
                buf -> new Quality(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    // ------------------------------------------------------------------ reputation

    /**
     * The add-on's reputation of the account ({@code ReputationService}).
     *
     * @param ruf      quality of the last 30 days, 0..1
     * @param measured whether there was any service in the window
     * @param uptime   share of servers that ran
     * @param overload how far serving servers ran over capacity
     * @param throttle share of the serving that ran throttled
     * @param crashes  crashes in the window
     * @param days     days with service in the window
     * @param renown   Bekanntheit: Data Center's reputation points
     * @param sources  renown booked per {@code Renown.Source}, in its order
     * @param pending  new customers still in an undisturbed first month
     * @param review   the last customer review {day, positive, product, segment, text, today}; empty before the first
     * @param campaigns the advertising campaigns that exist today
     * @param slaUptime uptime of the calendar month so far (-1 without service)
     */
    public record Standing(double ruf, boolean measured, double uptime, double overload, double throttle, int crashes, int days,
                           double renown, List<Double> sources, double pending, List<Long> review, List<Campaign> campaigns,
                           double slaUptime) {
        public static final Standing NONE = new Standing(1, false, 1, 0, 0, 0, 0, 0, List.of(), 0, List.of(), List.of(), -1);

        private static final StreamCodec<FriendlyByteBuf, Long> LONG = StreamCodec.of(
                (buf, v) -> buf.writeVarLong(v), FriendlyByteBuf::readVarLong);

        private static final StreamCodec<FriendlyByteBuf, Double> DOUBLE = StreamCodec.of(
                (buf, v) -> buf.writeDouble(v), FriendlyByteBuf::readDouble);

        public static final StreamCodec<FriendlyByteBuf, Standing> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeDouble(s.ruf);
                    buf.writeBoolean(s.measured);
                    buf.writeDouble(s.uptime);
                    buf.writeDouble(s.overload);
                    buf.writeDouble(s.throttle);
                    buf.writeVarInt(s.crashes);
                    buf.writeVarInt(s.days);
                    buf.writeDouble(s.renown);
                    writeList(buf, s.sources, DOUBLE);
                    buf.writeDouble(s.pending);
                    writeList(buf, s.review, LONG);
                    writeList(buf, s.campaigns, Campaign.STREAM_CODEC);
                    buf.writeDouble(s.slaUptime);
                },
                buf -> new Standing(buf.readDouble(), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readVarInt(), buf.readVarInt(), buf.readDouble(), readList(buf, DOUBLE, 32), buf.readDouble(),
                        readList(buf, LONG, 8), readList(buf, Campaign.STREAM_CODEC, 16),
                        buf.readDouble()));
    }

    /**
     * An advertising campaign ({@code Campaigns}).
     *
     * @param kind     {@code Campaigns.Campaign} ordinal
     * @param daysLeft days until it can be booked again (0 = now)
     */
    public record Campaign(int kind, long costCents, int renown, int daysLeft) {
        public static final StreamCodec<FriendlyByteBuf, Campaign> STREAM_CODEC = StreamCodec.of(
                (buf, c) -> {
                    buf.writeVarInt(c.kind);
                    buf.writeVarLong(c.costCents);
                    buf.writeVarInt(c.renown);
                    buf.writeVarInt(c.daysLeft);
                },
                buf -> new Campaign(buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt()));
    }

    // ------------------------------------------------------------------ codec

    public static final StreamCodec<FriendlyByteBuf, HostingSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeBoolean(s.connected);
                buf.writeVarInt(s.reputation);
                buf.writeVarInt(s.customerClass);
                buf.writeVarInt(s.foreignRacks);
                Network.STREAM_CODEC.encode(buf, s.network);
                writeList(buf, s.racks, Rack.STREAM_CODEC);
                writeList(buf, s.segments, Segment.STREAM_CODEC);
                writeList(buf, s.contracts, Contract.STREAM_CODEC);
                writeList(buf, s.deliveries, Delivery.STREAM_CODEC);
                Startup.STREAM_CODEC.encode(buf, s.startup);
                Market.STREAM_CODEC.encode(buf, s.market);
                Standing.STREAM_CODEC.encode(buf, s.standing);
                buf.writeDouble(s.ticksPerDay);
            },
            buf -> new HostingSnapshot(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    Network.STREAM_CODEC.decode(buf), readList(buf, Rack.STREAM_CODEC, 4096), readList(buf, Segment.STREAM_CODEC, 64),
                    readList(buf, Contract.STREAM_CODEC, 64), readList(buf, Delivery.STREAM_CODEC, 64), Startup.STREAM_CODEC.decode(buf),
                    Market.STREAM_CODEC.decode(buf), Standing.STREAM_CODEC.decode(buf), buf.readDouble()));

    private static <T> void writeList(FriendlyByteBuf buf, List<T> list, StreamCodec<FriendlyByteBuf, T> codec) {
        buf.writeVarInt(list.size());
        for (T element : list) {
            codec.encode(buf, element);
        }
    }

    /** A list of at most {@code max} elements (a guard against broken packets). */
    private static <T> List<T> readList(FriendlyByteBuf buf, StreamCodec<FriendlyByteBuf, T> codec, int max) {
        int size = Math.min(buf.readVarInt(), max);
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(codec.decode(buf));
        }
        return List.copyOf(list);
    }
}
