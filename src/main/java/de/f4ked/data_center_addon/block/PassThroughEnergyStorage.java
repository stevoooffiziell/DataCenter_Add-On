package de.f4ked.data_center_addon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * The energy port on one end of a Powered Server Room Panel. It stores nothing: every call is forwarded
 * to whatever touches the opposite end (a battery, a cable, a machine or the next panel in a chain).
 * The target is looked up on every call, so it is always current even when a neighbour caches this object.
 */
final class PassThroughEnergyStorage implements IEnergyStorage {
    /** Longest chain of panels a single transfer may pass through; also stops any accidental loop. */
    private static final int MAX_CHAIN = 64;
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private final Level level;
    private final BlockPos pos;
    /** The face of the panel this port is on. Energy comes in here and leaves through the opposite face. */
    private final Direction port;

    PassThroughEnergyStorage(Level level, BlockPos pos, Direction port) {
        this.level = level;
        this.pos = pos;
        this.port = port;
    }

    @Nullable
    private IEnergyStorage target() {
        BlockPos other = pos.relative(port.getOpposite());
        if (!level.isLoaded(other)) {
            return null;
        }
        // The target's face that touches the panel points back towards us, i.e. in the same direction as our port.
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, other, port);
    }

    private interface Call<T> {
        T apply(IEnergyStorage target);
    }

    private static <T> T forward(@Nullable IEnergyStorage target, T fallback, Call<T> call) {
        if (target == null) {
            return fallback;
        }
        int[] depth = DEPTH.get();
        if (depth[0] >= MAX_CHAIN) {
            return fallback;
        }
        depth[0]++;
        try {
            return call.apply(target);
        } finally {
            depth[0]--;
        }
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) return 0;
        return forward(target(), 0, t -> t.receiveEnergy(maxReceive, simulate));
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (maxExtract <= 0) return 0;
        return forward(target(), 0, t -> t.extractEnergy(maxExtract, simulate));
    }

    @Override
    public int getEnergyStored() {
        return forward(target(), 0, IEnergyStorage::getEnergyStored);
    }

    @Override
    public int getMaxEnergyStored() {
        return forward(target(), 0, IEnergyStorage::getMaxEnergyStored);
    }

    @Override
    public boolean canExtract() {
        return forward(target(), false, IEnergyStorage::canExtract);
    }

    @Override
    public boolean canReceive() {
        return forward(target(), false, IEnergyStorage::canReceive);
    }
}
