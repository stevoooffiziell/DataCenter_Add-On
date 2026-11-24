package de.f4ked.data_center_addon.hosting;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

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
                              Network network, List<Rack> racks, List<Segment> segments, List<Contract> contracts) {

    public static final HostingSnapshot EMPTY = new HostingSnapshot(false, 0, 0, 0, Network.NONE, List.of(), List.of(), List.of());

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
                           float metToday, int expiresIn) {
        public boolean covered() {
            return free >= required;
        }
    }

    public List<Contract> activeContracts() {
        return contracts.stream().filter(c -> !c.offer).toList();
    }

    public List<Contract> offers() {
        return contracts.stream().filter(Contract::offer).toList();
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
                }
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
                            buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readFloat(), buf.readVarInt()));
                }
                return new HostingSnapshot(connected, reputation, customerClass, foreign, network, List.copyOf(racks),
                        List.copyOf(segments), List.copyOf(contracts));
            });
}
