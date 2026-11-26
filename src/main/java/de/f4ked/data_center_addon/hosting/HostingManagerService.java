package de.f4ked.data_center_addon.hosting;

import de.f4ked.data_center_addon.hosting.reputation.ClassRules;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.hosting.CustomerSegment;
import com.example.datacenter.hosting.HostingCustomers;
import com.example.datacenter.hosting.HostingMode;
import com.example.datacenter.hosting.HostingProduct;
import com.example.datacenter.hosting.HostingRackBlockEntity;
import com.example.datacenter.hosting.HostingReputation;
import com.example.datacenter.mining.MiningEra;
import com.example.datacenter.net.ComputeGrid;
import com.example.datacenter.net.ComputeNet;
import com.example.datacenter.net.ComputeNets;
import com.example.datacenter.net.NetNode;
import com.example.datacenter.net.NetStatus;
import com.example.datacenter.wallet.OwnerMessages;
import de.f4ked.data_center_addon.hosting.contract.ContractBook;
import de.f4ked.data_center_addon.hosting.contract.ContractService;
import de.f4ked.data_center_addon.hosting.contract.DeliveryService;
import de.f4ked.data_center_addon.hosting.market.MarketService;
import de.f4ked.data_center_addon.hosting.reputation.ReputationService;
import de.f4ked.data_center_addon.hosting.startup.StartupService;
import de.f4ked.data_center_addon.mixin.ComputeNetInvoker;
import de.f4ked.data_center_addon.network.NetworkSwitchBlockEntity;
import de.f4ked.data_center_addon.network.NetworkTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the Hosting Manager: finds the hosting racks on the compute network a manager is
 * plugged into, reads their figures and carries out the player's changes.
 * <p>
 * Customer segments: Data Center keeps one customer book per owner and product, shared by all of
 * that owner's racks selling the product. The manager shows the part of each book that sits on the
 * racks of its network (scaled by those racks' customers), and splits the racks' income over the
 * segments by what each segment pays.
 */
public final class HostingManagerService {
    private HostingManagerService() {
    }

    /** The changes the manager can make to a rack; the same steps as the rack's own menu buttons. */
    public enum Action {
        PRODUCT_PREVIOUS, PRODUCT_NEXT, FILL_DOWN, FILL_UP
    }

    /** Compute nets the manager touches through a cable or switch next to it. */
    private static Set<ComputeNet> netsAround(ServerLevel level, BlockPos manager) {
        ComputeGrid grid = ComputeNets.grid(level);
        Set<ComputeNet> nets = new LinkedHashSet<>();
        for (Direction direction : Direction.values()) {
            NetNode node = grid.nodeAt(manager.relative(direction));
            if (node != null && node.isConductor() && node.net() != null) {
                nets.add(node.net());
            }
        }
        return nets;
    }

    /** Every hosting rack on the manager's network. */
    static List<HostingRackBlockEntity> racks(ServerLevel level, BlockPos manager) {
        List<HostingRackBlockEntity> racks = new ArrayList<>();
        Set<BlockPos> seen = new java.util.HashSet<>();
        for (ComputeNet net : netsAround(level, manager)) {
            for (NetNode node : List.copyOf(((ComputeNetInvoker) (Object) net).data_center_addon$members())) {
                if (node.isDevice() && seen.add(node.pos())
                        && level.getBlockEntity(node.pos()) instanceof HostingRackBlockEntity rack) {
                    racks.add(rack);
                }
            }
        }
        racks.sort(Comparator.comparingLong(rack -> rack.getBlockPos().asLong()));
        return racks;
    }

    public static HostingSnapshot snapshot(ServerLevel level, BlockPos manager, ServerPlayer player) {
        boolean connected = !netsAround(level, manager).isEmpty();
        List<HostingSnapshot.Rack> racks = new ArrayList<>();
        int foreign = 0;
        // per owner and product: customers and income on this network's racks
        Map<Pool, double[]> pools = new HashMap<>();
        for (HostingRackBlockEntity rack : racks(level, manager)) {
            BlockPos pos = rack.getBlockPos();
            if (!DataCenterApi.mayControl(level, pos, player)) {
                foreign++;
                continue;
            }
            HostingProduct product = rack.getProduct();
            long centsPerDay = centsPerDay(rack);
            racks.add(new HostingSnapshot.Rack(pos, product == null ? -1 : product.ordinal(), rack.getModeCustomers(),
                    rack.getModeCapacity(), centsPerDay, rack.getModeStatus(), rack.getHostingFill(),
                    licenceBound(product, rack, centsPerDay)));
            if (product != null) {
                double[] pool = pools.computeIfAbsent(new Pool(OwnerMessages.ownerOf(level, pos), product), p -> new double[2]);
                pool[0] += rack.getModeCustomers();
                pool[1] += centsPerDay;
            }
        }

        UUID account = DataCenterApi.accountOf(level, player.getUUID());
        HostingReputation reputation = HostingReputation.get(level, account);
        return new HostingSnapshot(connected, (int) Math.round(reputation.getPoints()),
                ClassRules.tier(level.getServer(), account).ordinal(),
                foreign, network(level, manager), List.copyOf(racks), segments(level, pools), ContractService.snapshot(level, account),
                DeliveryService.snapshot(level, account, ContractBook.get(level.getServer()).account(account), player.getInventory()),
                StartupService.snapshot(level, account), MarketService.snapshot(level, account),
                ReputationService.snapshot(level, account),
                24.0 / MiningEra.fictionalHoursPerTick());
    }

    /**
     * Whether a shared hosting rack has customers but earns nothing because of the control panel
     * licence. Data Center bills each server {@code paying * unitNet - 30} USD a month (the licence
     * applies with software enabled, to every shared offer but game servers) and cuts negative results
     * to zero, so a running rack whose servers each hold too few customers shows $0.
     */
    public static boolean licenceBound(@Nullable HostingProduct product, HostingRackBlockEntity rack, long centsPerDay) {
        return product != null && product.mode() == HostingMode.SHARED && product != HostingProduct.SHARED_GAME
                && com.example.datacenter.Config.SOFTWARE_ENABLED.get()
                && rack.getModeStatus() == 0 && rack.getModeCustomers() >= 0.5 && centsPerDay <= 0;
    }

    /** A rack's income per in-game day in US cents, as the manager shows it. */
    public static long centsPerDay(HostingRackBlockEntity rack) {
        return Math.round(rack.getLastUsdCentsPerTick() * 24.0 / MiningEra.fictionalHoursPerTick());
    }

    /** Capacity and use of the manager's network, with the add-on's cable warnings. */
    private static HostingSnapshot.Network network(ServerLevel level, BlockPos manager) {
        Set<ComputeNet> nets = netsAround(level, manager);
        if (nets.isEmpty()) {
            return HostingSnapshot.Network.NONE;
        }
        ComputeNet net = nets.iterator().next();
        NetStatus status = net.status(ComputeNets.grid(level).limits());
        int overlong = 0, unpowered = 0;
        NetworkTopology.Layout layout = NetworkTopology.layoutAt(level, ((ComputeNetInvoker) (Object) net).data_center_addon$members().iterator().next().pos());
        if (layout != null) {
            for (NetworkTopology.Run run : layout.runs()) {
                if (run.overlength()) {
                    overlong++;
                }
            }
            for (BlockPos pos : layout.switches()) {
                if (level.getBlockEntity(pos) instanceof NetworkSwitchBlockEntity be && !be.isPowered()) {
                    unpowered++;
                }
            }
        }
        return new HostingSnapshot.Network(status.capacity(), status.delivered(), overlong, unpowered);
    }

    private record Pool(@Nullable UUID owner, HostingProduct product) {
    }

    private static List<HostingSnapshot.Segment> segments(ServerLevel level, Map<Pool, double[]> pools) {
        CustomerSegment[] all = CustomerSegment.values();
        double[] customers = new double[all.length];
        double[] cents = new double[all.length];
        HostingCustomers book = HostingCustomers.get(level);
        for (Map.Entry<Pool, double[]> entry : pools.entrySet()) {
            double[] counts = book.segments(entry.getKey().owner, entry.getKey().product);
            double total = 0, paid = 0;
            for (int i = 0; i < counts.length && i < all.length; i++) {
                total += counts[i];
                paid += counts[i] * all[i].pay;
            }
            if (total <= 0) {
                continue;
            }
            double share = Math.min(1.0, entry.getValue()[0] / total);
            for (int i = 0; i < counts.length && i < all.length; i++) {
                customers[i] += counts[i] * share;
                if (paid > 0) {
                    cents[i] += entry.getValue()[1] * counts[i] * all[i].pay / paid;
                }
            }
        }
        List<HostingSnapshot.Segment> segments = new ArrayList<>();
        for (int i = 0; i < all.length; i++) {
            if (customers[i] >= 0.5 || cents[i] >= 1) {
                segments.add(new HostingSnapshot.Segment(i, customers[i], Math.round(cents[i])));
            }
        }
        segments.sort(Comparator.comparingDouble(HostingSnapshot.Segment::customers).reversed());
        return List.copyOf(segments);
    }

    /**
     * Carries out a change from the manager screen. The rack must be on the manager's network and the
     * player must be allowed to control it, the same check Data Center does for its own menus.
     */
    public static void apply(ServerPlayer player, BlockPos manager, BlockPos rackPos, Action action) {
        if (!(player.containerMenu instanceof HostingManagerMenu menu) || !menu.managerPos().equals(manager)
                || !(player.level() instanceof ServerLevel level) || !DataCenterApi.mayControl(level, rackPos, player)) {
            return;
        }
        for (HostingRackBlockEntity rack : racks(level, manager)) {
            if (Objects.equals(rack.getBlockPos(), rackPos)) {
                switch (action) {
                    case PRODUCT_PREVIOUS -> rack.stepMode(-1);
                    case PRODUCT_NEXT -> rack.stepMode(1);
                    case FILL_DOWN -> rack.stepFill(-1);
                    case FILL_UP -> rack.stepFill(1);
                }
                return;
            }
        }
    }
}
