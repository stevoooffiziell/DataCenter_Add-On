package de.f4ked.data_center_addon.industry.chipfab;

import de.f4ked.data_center_addon.industry.IndustryContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * The chip production line: a controller and every cleanroom wall and lithography unit connected to it
 * (touching each other face to face, up to {@link #MAX_BLOCKS} blocks).
 * <ul>
 *   <li>It needs at least {@link #MIN_WALLS} cleanroom walls and one lithography unit.</li>
 *   <li>Each lithography unit adds one step of speed, but needs {@link #WALLS_PER_UNIT} cleanroom walls;
 *       units without enough walls stand idle. At most {@link #MAX_UNITS} count.</li>
 * </ul>
 */
public final class ChipFabStructure {
    public static final int MIN_WALLS = 8, WALLS_PER_UNIT = 4, MAX_UNITS = 8, MAX_BLOCKS = 96;

    /** What the scan found; {@code speed} is the number of working lithography units (0 = not formed). */
    public record Scan(int walls, int units, Status status) {
        public int speed() {
            return status == Status.FORMED ? Math.min(MAX_UNITS, Math.min(units, walls / WALLS_PER_UNIT)) : 0;
        }
    }

    /** The state of the line; the name gives {@code data_center_addon.industry.chip_fab.status.<name>}. */
    public enum Status { FORMED, NO_WALLS, NO_UNIT }

    private ChipFabStructure() {
    }

    public static Scan scan(Level level, BlockPos controller) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(controller);
        seen.add(controller);
        int walls = 0, units = 0;
        while (!queue.isEmpty() && seen.size() <= MAX_BLOCKS) {
            BlockPos pos = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos next = pos.relative(d);
                if (seen.contains(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                boolean wall = state.is(IndustryContent.CLEANROOM_WALL.get());
                boolean unit = state.is(IndustryContent.LITHOGRAPHY_UNIT.get());
                if (wall || unit) {
                    seen.add(next);
                    queue.add(next);
                    if (wall) {
                        walls++;
                    } else {
                        units++;
                    }
                }
            }
        }
        Status status = walls < MIN_WALLS ? Status.NO_WALLS : units == 0 ? Status.NO_UNIT : Status.FORMED;
        return new Scan(walls, units, status);
    }
}
