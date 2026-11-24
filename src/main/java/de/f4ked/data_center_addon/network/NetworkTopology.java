package de.f4ked.data_center_addon.network;

import com.example.datacenter.net.CableGrade;
import com.example.datacenter.net.ComputeGrid;
import com.example.datacenter.net.ComputeNet;
import com.example.datacenter.net.ComputeNets;
import com.example.datacenter.net.NetLimits;
import com.example.datacenter.net.NetNode;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.mixin.ComputeGridInvoker;
import de.f4ked.data_center_addon.mixin.ComputeNetInvoker;
import de.f4ked.data_center_addon.mixin.ComputeNetsAccessor;
import de.f4ked.data_center_addon.mixin.NetNodeAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The capacity of a compute network that contains the add-on's cables or switches.
 * <p>
 * Data Center gives a whole network one capacity: that of its weakest cable. With the add-on's parts:
 * <ul>
 *   <li>A <b>run</b> is a connected stretch of cable between switches. Its capacity is that of its
 *       weakest cable, or a tenth of it when the run is longer than that cable's reach.</li>
 *   <li><b>Without a switch</b> all cables form one run, so the weakest cable limits everything.</li>
 *   <li><b>With switches</b> every run counts on its own: production can leave the runs as fast as
 *       each run carries it, demand can be fed as fast as each run carries it, and the largest switch's
 *       backplane limits the total. A star (one run per rack) beats a chain.</li>
 *   <li>A switch without power cuts the network.</li>
 * </ul>
 * Called from {@code ComputeNetMixin}; Data Center's own cable limit still applies on top.
 */
public final class NetworkTopology {
    private static final Map<ComputeNet, Layout> LAYOUTS = new WeakHashMap<>();
    private static final Map<ComputeGrid, ResourceKey<Level>> LEVELS = new WeakHashMap<>();

    private NetworkTopology() {
    }

    /**
     * A cable run: its capacity, the devices plugged into it and the cable blocks it is made of.
     * {@code reach} is the shortest reach of its cables ({@link Integer#MAX_VALUE}: no limit).
     */
    public record Run(long capacity, CableType weakest, int length, int reach, boolean overlength, List<NetNode> devices,
                      Set<Long> cells) {
        public boolean contains(BlockPos pos) {
            return cells.contains(pos.asLong());
        }
    }

    /** The parts of a network that do not change until a block is placed or removed. */
    public record Layout(int version, boolean hasAddonParts, List<Run> runs, List<BlockPos> switches,
                         long backplane, List<NetNode> switchDevices) {
        static final Layout NONE = new Layout(-1, false, List.of(), List.of(), 0, List.of());
    }

    /** Compute per tick the network passes, or {@link Long#MAX_VALUE} if the add-on's parts do not limit it. */
    public static synchronized long capacity(ComputeNet net, Set<NetNode> members) {
        if (members.isEmpty()) {
            return Long.MAX_VALUE;
        }
        ComputeGrid grid = ((NetNodeAccessor) (Object) members.iterator().next()).data_center_addon$grid();
        ServerLevel level = levelOf(grid);
        if (level == null) {
            return Long.MAX_VALUE;
        }
        Layout layout = layout(net, members, grid, level);
        if (!layout.hasAddonParts) {
            return Long.MAX_VALUE;
        }
        if (layout.switches.isEmpty()) {
            long cap = Long.MAX_VALUE;
            for (Run run : layout.runs) {
                cap = Math.min(cap, run.capacity);
            }
            return cap;
        }
        for (BlockPos pos : layout.switches) {
            if (!(level.getBlockEntity(pos) instanceof NetworkSwitchBlockEntity be) || !be.isPowered()) {
                return 0;
            }
        }
        // devices plugged straight into a switch have no cable in between
        long out = 0, in = 0;
        for (NetNode device : layout.switchDevices) {
            out = saturatingAdd(out, device.reportedOutput());
            in = saturatingAdd(in, device.reportedDemand());
        }
        for (Run run : layout.runs) {
            long runOut = 0, runIn = 0;
            for (NetNode device : run.devices) {
                runOut = saturatingAdd(runOut, device.reportedOutput());
                runIn = saturatingAdd(runIn, device.reportedDemand());
            }
            out = saturatingAdd(out, Math.min(run.capacity, runOut));
            in = saturatingAdd(in, Math.min(run.capacity, runIn));
        }
        return Math.min(Math.min(out, in), layout.backplane);
    }

    /** The cached layout of a network, rebuilt after every change to the grid's structure. */
    public static synchronized Layout layout(ComputeNet net, Set<NetNode> members, ComputeGrid grid, ServerLevel level) {
        int version = ((ComputeGridInvoker) (Object) grid).data_center_addon$structureVersion();
        Layout cached = LAYOUTS.get(net);
        if (cached != null && cached.version == version) {
            return cached;
        }
        Layout built = build(members, grid.limits(), level, version);
        LAYOUTS.put(net, built);
        return built;
    }

    private record Cell(long capacity, int reach, @Nullable CableType type) {
    }

    private static Layout build(Set<NetNode> members, NetLimits limits, ServerLevel level, int version) {
        Map<Long, NetNode> byKey = new HashMap<>();
        Map<Long, Cell> cells = new HashMap<>();
        List<BlockPos> switches = new ArrayList<>();
        long backplane = 0;
        boolean addon = false;
        for (NetNode node : members) {
            byKey.put(node.pos().asLong(), node);
            if (node.isDevice()) {
                for (BlockPos port : ((NetNodeAccessor) (Object) node).data_center_addon$ports()) {
                    byKey.put(port.asLong(), node);
                }
                continue;
            }
            Block block = level.isLoaded(node.pos()) ? level.getBlockState(node.pos()).getBlock() : null;
            if (block instanceof NetworkSwitchBlock sw) {
                switches.add(node.pos());
                backplane = Math.max(backplane, sw.model().backplane());
                addon = true;
            } else if (block instanceof DataCableBlock cable) {
                CableType type = cable.type();
                cells.put(node.pos().asLong(), new Cell(type.capacity(), Config.cableReachLimits() ? type.reach : Integer.MAX_VALUE, type));
                addon = true;
            } else {
                CableGrade grade = node.grade();
                cells.put(node.pos().asLong(), new Cell(grade == null ? Long.MAX_VALUE : Math.max(0, limits.capacity(grade)), Integer.MAX_VALUE, null));
            }
        }
        if (!addon) {
            return new Layout(version, false, List.of(), List.of(), 0, List.of());
        }

        Set<Long> switchKeys = new HashSet<>();
        for (BlockPos pos : switches) {
            switchKeys.add(pos.asLong());
        }
        List<Run> runs = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long start : cells.keySet()) {
            if (!seen.add(start)) {
                continue;
            }
            long capacity = Long.MAX_VALUE;
            int reach = Integer.MAX_VALUE;
            CableType weakest = null;
            int length = 0;
            Set<NetNode> devices = new HashSet<>();
            Set<Long> runCells = new HashSet<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                long key = queue.poll();
                Cell cell = cells.get(key);
                runCells.add(key);
                length++;
                if (cell.capacity < capacity) {
                    capacity = cell.capacity;
                }
                if (cell.type != null && (weakest == null || cell.type.capacity() < weakest.capacity())) {
                    weakest = cell.type;
                }
                reach = Math.min(reach, cell.reach);
                BlockPos pos = BlockPos.of(key);
                for (Direction direction : Direction.values()) {
                    long next = pos.relative(direction).asLong();
                    if (cells.containsKey(next)) {
                        if (seen.add(next)) {
                            queue.add(next);
                        }
                    } else if (!switchKeys.contains(next)) {
                        NetNode node = byKey.get(next);
                        if (node != null && node.isDevice()) {
                            devices.add(node);
                        }
                    }
                }
            }
            boolean overlength = length > reach;
            long runCapacity = overlength ? capacity / CableType.OVERLENGTH_DIVISOR : capacity;
            runs.add(new Run(runCapacity, weakest, length, reach, overlength, List.copyOf(devices), Set.copyOf(runCells)));
        }

        Set<NetNode> switchDevices = new HashSet<>();
        for (BlockPos pos : switches) {
            for (Direction direction : Direction.values()) {
                NetNode node = byKey.get(pos.relative(direction).asLong());
                if (node != null && node.isDevice()) {
                    switchDevices.add(node);
                }
            }
        }
        return new Layout(version, true, List.copyOf(runs), List.copyOf(switches), backplane, List.copyOf(switchDevices));
    }

    /** The layout of the network at a position, or {@code null} if there is none. */
    @Nullable
    public static Layout layoutAt(ServerLevel level, BlockPos pos) {
        ComputeGrid grid = ComputeNets.grid(level);
        ComputeNet net = grid.netAt(pos);
        if (net == null) {
            return null;
        }
        Set<NetNode> members = ((ComputeNetInvoker) (Object) net).data_center_addon$members();
        return members.isEmpty() ? null : layout(net, members, grid, level);
    }

    /** The cable run a cable block belongs to, or {@code null} if it is not part of a network with the add-on's parts. */
    @Nullable
    public static Run runAt(ServerLevel level, BlockPos pos) {
        Layout layout = layoutAt(level, pos);
        if (layout == null) {
            return null;
        }
        for (Run run : layout.runs()) {
            if (run.contains(pos)) {
                return run;
            }
        }
        return null;
    }

    /** Makes Data Center recompute the network at a position, e.g. after a switch lost power. */
    public static void invalidate(@Nullable Level level, BlockPos pos) {
        if (level == null || level.isClientSide) {
            return;
        }
        ComputeNet net = ComputeNets.grid(level).netAt(pos);
        if (net != null) {
            ((ComputeNetInvoker) (Object) net).data_center_addon$invalidate();
        }
    }

    @Nullable
    private static ServerLevel levelOf(ComputeGrid grid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        ResourceKey<Level> key = LEVELS.get(grid);
        if (key == null) {
            for (Map.Entry<ResourceKey<Level>, ComputeNets> entry : ComputeNetsAccessor.data_center_addon$byLevel().entrySet()) {
                if (((ComputeNetsAccessor) (Object) entry.getValue()).data_center_addon$grid() == grid) {
                    key = entry.getKey();
                    LEVELS.put(grid, key);
                    break;
                }
            }
        }
        return key == null ? null : server.getLevel(key);
    }

    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }
}
