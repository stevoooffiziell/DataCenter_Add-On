package de.f4ked.data_center_addon.network;

import de.f4ked.data_center_addon.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Powers a switch: it draws its model's wattage in FE every tick. Without power it passes nothing,
 * which {@link NetworkTopology} reads through {@link #isPowered()}.
 */
public class NetworkSwitchBlockEntity extends BlockEntity {
    private final SwitchModel model;
    private final SwitchEnergy energy;
    private boolean powered;

    public NetworkSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(NetworkContent.SWITCH_ENTITY.get(), pos, state);
        this.model = state.getBlock() instanceof NetworkSwitchBlock block ? block.model() : SwitchModel.HUB_3COM_SUPERSTACK_II;
        int draw = Math.max(1, model.watts);
        // a second of buffer; accepts up to that per tick so one cable can fill it
        this.energy = new SwitchEnergy(draw * 20, draw * 20);
    }

    public SwitchModel model() {
        return model;
    }

    public IEnergyStorage energy() {
        return energy;
    }

    public boolean isPowered() {
        return powered || !Config.switchesNeedPower();
    }

    void serverTick() {
        boolean now = Config.switchesNeedPower() && energy.consume(model.watts);
        if (now != powered) {
            powered = now;
            setChanged();
            NetworkTopology.invalidate(level, worldPosition);
        }
    }

    /** Accepts FE from cables but never gives any out; the switch itself uses it up. */
    private static final class SwitchEnergy extends EnergyStorage {
        SwitchEnergy(int capacity, int maxReceive) {
            super(capacity, maxReceive, 0);
        }

        boolean consume(int amount) {
            if (energy < amount) {
                return false;
            }
            energy -= amount;
            return true;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
        tag.putBoolean("powered", powered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) {
            energy.deserializeNBT(registries, tag.get("energy"));
        }
        powered = tag.getBoolean("powered");
    }
}
