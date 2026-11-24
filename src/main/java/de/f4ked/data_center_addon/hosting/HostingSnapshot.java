package de.f4ked.data_center_addon.hosting;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Hosting Manager shows: the hosting racks reachable over its network, the customer
 * segments on them, and the totals. Built on the server ({@link HostingManagerService}), sent to the
 * player who has the manager open.
 *
 * @param connected   whether a cable is plugged into the manager
 * @param foreignRacks racks in the network the player may not control (not listed)
 * @param network     state of the compute network the manager is plugged into
 */
public record HostingSnapshot(boolean connected, int reputation, int customerClass, int foreignRacks,
                              Network network, List<Rack> racks, List<Segment> segments, List<Contract> contracts,
                              List<Delivery> deliveries, Startup startup) {

    public static final HostingSnapshot EMPTY = new HostingSnapshot(false, 0, 0, 0, Network.NONE, List.of(), List.of(), List.of(),
            List.of(), Startup.NONE);

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
    }

    /**
     * Annual plans paid in advance.
     *
     * @param available  prepayment is turned on in the config
     * @param enabled    the account offers annual plans to new customers
     * @param openCents  value not yet settled against the income (plans running plus what is still to be taken)
     */
    public record Prepay(boolean available, boolean enabled, int sharePercent, int discountPercent, long receivedCents,
                         long openCents, int customers) {
        public static final Prepay NONE = new Prepay(false, false, 0, 0, 0, 0, 0);
    }

    /** A service ticket: right-click the rack at {@code pos} before it runs out. */
    public record Ticket(int id, BlockPos pos, int kind, long rewardCents, int daysLeft) {
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
    }

    /**
     * One goal of a delivery.
     *
     * @param kind        {@code DeliveryService.GoalKind} ordinal
     * @param item        the item wanted (ITEM) or shown as icon
     * @param inInventory matching items in the viewing player's inventory
     */
    public record Goal(int kind, ResourceLocation item, int count, int delivered, int inInventory) {
    }

    public List<Delivery> runningDeliveries() {
        return deliveries.stream().filter(d -> !d.offer).toList();
    }

    public List<Delivery> deliveryOffers() {
        return deliveries.stream().filter(Delivery::offer).toList();
    }

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
    }

    public List<Contract> activeContracts() {
        return contracts.stream().filter(c -> !c.offer).toList();
    }

    public List<Contract> offers() {
        return contracts.stream().filter(Contract::offer).toList();
    }

    /** What the running contracts pay per in-game day (paid for every day their places are kept free). */
    public long contractCentsPerDay() {
        return activeContracts().stream().mapToLong(Contract::centsPerDay).sum();
    }

    /** Places the running contracts keep reserved. */
    public int contractAccounts() {
        return activeContracts().stream().mapToInt(Contract::accounts).sum();
    }

    /** Income per in-game day of the racks plus the running contracts. */
    public long incomeCentsPerDay() {
        return totalCentsPerDay() + contractCentsPerDay();
    }

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
    }

    /**
     * One hosting rack.
     *
     * @param product    {@code HostingProduct} ordinal, -1 = dedicated servers
     * @param customers  customers on this rack
     * @param capacity   customers it can take
     * @param centsPerDay income per in-game day
     * @param status     {@code HostingRackBlockEntity.MODE_*}
     * @param fill       target fill in percent (shared hosting and VPS)
     * @param licenceBound shared hosting rack with customers that earns nothing: on every server the
     *                   control panel licence ($30 a month) is higher than what its customers pay
     */
    public record Rack(BlockPos pos, int product, double customers, double capacity, long centsPerDay, int status, int fill,
                       boolean licenceBound) {
    }

    /** Racks of this snapshot that earn nothing because of the control panel licence. */
    public int licenceBoundRacks() {
        int count = 0;
        for (Rack rack : racks) {
            if (rack.licenceBound) {
                count++;
            }
        }
        return count;
    }

    /** Customers of one {@code CustomerSegment} on the racks of this network, and their share of the income. */
    public record Segment(int segment, double customers, long centsPerDay) {
    }

    public double totalCustomers() {
        double sum = 0;
        for (Rack rack : racks) {
            sum += rack.customers;
        }
        return sum;
    }

    public long totalCentsPerDay() {
        long sum = 0;
        for (Rack rack : racks) {
            sum += rack.centsPerDay;
        }
        return sum;
    }

    public static final StreamCodec<FriendlyByteBuf, HostingSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeBoolean(s.connected);
                buf.writeVarInt(s.reputation);
                buf.writeVarInt(s.customerClass);
                buf.writeVarInt(s.foreignRacks);
                buf.writeVarLong(s.network.capacity);
                buf.writeVarLong(s.network.delivered);
                buf.writeVarInt(s.network.overlongRuns);
                buf.writeVarInt(s.network.unpoweredSwitches);
                buf.writeVarInt(s.racks.size());
                for (Rack r : s.racks) {
                    buf.writeBlockPos(r.pos);
                    buf.writeVarInt(r.product + 1);
                    buf.writeDouble(r.customers);
                    buf.writeDouble(r.capacity);
                    buf.writeVarLong(r.centsPerDay);
                    buf.writeVarInt(r.status);
                    buf.writeVarInt(r.fill);
                    buf.writeBoolean(r.licenceBound);
                }
                buf.writeVarInt(s.segments.size());
                for (Segment g : s.segments) {
                    buf.writeVarInt(g.segment);
                    buf.writeDouble(g.customers);
                    buf.writeVarLong(g.centsPerDay);
                }
                buf.writeVarInt(s.contracts.size());
                for (Contract c : s.contracts) {
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
                }
                buf.writeVarInt(s.deliveries.size());
                for (Delivery d : s.deliveries) {
                    buf.writeVarInt(d.id);
                    buf.writeBoolean(d.offer);
                    buf.writeVarInt(d.client);
                    buf.writeVarInt(d.goals.size());
                    for (Goal g : d.goals) {
                        buf.writeVarInt(g.kind);
                        buf.writeResourceLocation(g.item);
                        buf.writeVarInt(g.count);
                        buf.writeVarInt(g.delivered);
                        buf.writeVarInt(g.inInventory);
                    }
                    buf.writeVarLong(d.payCents);
                    buf.writeVarInt(d.reputation);
                    buf.writeVarInt(d.days);
                    buf.writeVarInt(d.daysLeft);
                }
                Startup st = s.startup;
                buf.writeBoolean(st.started);
                buf.writeBoolean(st.veteran);
                buf.writeDouble(st.bonusFactor);
                buf.writeVarInt(st.bonusMode);
                buf.writeVarInt(st.bonusDaysLeft);
                buf.writeVarLong(st.bonusRevenueLeft);
                buf.writeVarLong(st.bonusPaidCents);
                buf.writeVarLong(st.grantCents);
                buf.writeVarLong(st.grantOfferCents);
                buf.writeVarLong(st.feesPaidCents);
                buf.writeVarLong(st.loanOfferCents);
                buf.writeBoolean(st.loanTaken);
                buf.writeVarLong(st.loanOutstandingCents);
                buf.writeVarInt(st.loanGraceDays);
                buf.writeVarLong(st.loanInstalmentCents);
                buf.writeLong(st.milestones);
                buf.writeVarInt(st.milestoneRewards.size());
                for (long reward : st.milestoneRewards) {
                    buf.writeVarLong(reward);
                }
                buf.writeVarInt(st.tickets.size());
                for (Ticket t : st.tickets) {
                    buf.writeVarInt(t.id);
                    buf.writeBlockPos(t.pos);
                    buf.writeVarInt(t.kind);
                    buf.writeVarLong(t.rewardCents);
                    buf.writeVarInt(t.daysLeft);
                }
                Prepay pp = st.prepay;
                buf.writeBoolean(pp.available);
                buf.writeBoolean(pp.enabled);
                buf.writeVarInt(pp.sharePercent);
                buf.writeVarInt(pp.discountPercent);
                buf.writeVarLong(pp.receivedCents);
                buf.writeVarLong(pp.openCents);
                buf.writeVarInt(pp.customers);
            },
            buf -> {
                boolean connected = buf.readBoolean();
                int reputation = buf.readVarInt();
                int customerClass = buf.readVarInt();
                int foreign = buf.readVarInt();
                Network network = new Network(buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt());
                int rackCount = Math.min(buf.readVarInt(), 4096);
                List<Rack> racks = new ArrayList<>(rackCount);
                for (int i = 0; i < rackCount; i++) {
                    racks.add(new Rack(buf.readBlockPos(), buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(),
                            buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
                }
                int segmentCount = Math.min(buf.readVarInt(), 64);
                List<Segment> segments = new ArrayList<>(segmentCount);
                for (int i = 0; i < segmentCount; i++) {
                    segments.add(new Segment(buf.readVarInt(), buf.readDouble(), buf.readVarLong()));
                }
                int contractCount = Math.min(buf.readVarInt(), 64);
                List<Contract> contracts = new ArrayList<>(contractCount);
                for (int i = 0; i < contractCount; i++) {
                    contracts.add(new Contract(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                            buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readBoolean(),
                            buf.readBoolean()));
                }
                int deliveryCount = Math.min(buf.readVarInt(), 64);
                List<Delivery> deliveries = new ArrayList<>(deliveryCount);
                for (int i = 0; i < deliveryCount; i++) {
                    int id = buf.readVarInt();
                    boolean offer = buf.readBoolean();
                    int client = buf.readVarInt();
                    int goalCount = Math.min(buf.readVarInt(), 8);
                    List<Goal> goals = new ArrayList<>(goalCount);
                    for (int j = 0; j < goalCount; j++) {
                        goals.add(new Goal(buf.readVarInt(), buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(),
                                buf.readVarInt()));
                    }
                    deliveries.add(new Delivery(id, offer, client, List.copyOf(goals), buf.readVarLong(), buf.readVarInt(),
                            buf.readVarInt(), buf.readVarInt()));
                }
                boolean started = buf.readBoolean();
                boolean veteran = buf.readBoolean();
                double bonusFactor = buf.readDouble();
                int bonusMode = buf.readVarInt();
                int bonusDaysLeft = buf.readVarInt();
                long bonusRevenueLeft = buf.readVarLong();
                long bonusPaid = buf.readVarLong();
                long grant = buf.readVarLong();
                long grantOffer = buf.readVarLong();
                long fees = buf.readVarLong();
                long loanOffer = buf.readVarLong();
                boolean loanTaken = buf.readBoolean();
                long loanOutstanding = buf.readVarLong();
                int loanGrace = buf.readVarInt();
                long loanInstalment = buf.readVarLong();
                long milestones = buf.readLong();
                int rewardCount = Math.min(buf.readVarInt(), 64);
                List<Long> rewards = new ArrayList<>(rewardCount);
                for (int i = 0; i < rewardCount; i++) {
                    rewards.add(buf.readVarLong());
                }
                int ticketCount = Math.min(buf.readVarInt(), 16);
                List<Ticket> tickets = new ArrayList<>(ticketCount);
                for (int i = 0; i < ticketCount; i++) {
                    tickets.add(new Ticket(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt()));
                }
                Prepay prepay = new Prepay(buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(),
                        buf.readVarLong(), buf.readVarInt());
                Startup startup = new Startup(started, veteran, bonusFactor, bonusMode, bonusDaysLeft, bonusRevenueLeft, bonusPaid,
                        grant, grantOffer, fees, loanOffer, loanTaken, loanOutstanding, loanGrace, loanInstalment, milestones,
                        List.copyOf(rewards), List.copyOf(tickets), prepay);
                return new HostingSnapshot(connected, reputation, customerClass, foreign, network, List.copyOf(racks),
                        List.copyOf(segments), List.copyOf(contracts), List.copyOf(deliveries), startup);
            });
}
