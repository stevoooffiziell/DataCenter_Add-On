package de.f4ked.data_center_addon.network;

import com.example.datacenter.net.ComputeGrid;
import com.example.datacenter.net.ComputeNets;
import com.example.datacenter.net.NetNode;
import com.example.datacenter.net.NetStatus;
import de.f4ked.data_center_addon.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Powers a switch: it draws its model's wattage in FE every tick. Without power it passes nothing,
 * which {@link NetworkTopology} reads through {@link #isPowered()}.
 * <p>
 * Once a second it also measures how busy its network is ({@link #activity()}) and sends that and its
 * power state to the clients, which blink the port LEDs on its front accordingly. Losing or regaining
 * power beeps.
 */
public class NetworkSwitchBlockEntity extends BlockEntity {
    /** No traffic. */
    public static final int ACTIVITY_IDLE = 0;
    /** The network passes nearly all it can: the cables or the backplane are the limit. */
    public static final int ACTIVITY_SATURATED = 4;
    private static final int SAMPLE_TICKS = 20;

    private final SwitchModel model;
    private final SwitchEnergy energy;
    private boolean powered;
    /** {@link #ACTIVITY_IDLE} .. {@link #ACTIVITY_SATURATED}; synced to the client. */
    private int activity;
    /** Delivered / capacity of the network in percent, -1 if the cables do not limit it; for Jade. */
    private int loadPercent = -1;

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

    /** How busy the network is, as last measured: {@link #ACTIVITY_IDLE} .. {@link #ACTIVITY_SATURATED}. */
    public int activity() {
        return activity;
    }

    /** Use of the network's capacity in percent, or -1 if its cables do not limit it. */
    public int loadPercent() {
        return loadPercent;
    }

    void serverTick() {
        if (level == null) {
            return;
        }
        boolean now = Config.switchesNeedPower() && energy.consume(model.watts);
        if (now != powered) {
            boolean wasPowered = powered;
            powered = now;
            setChanged();
            NetworkTopology.invalidate(level, worldPosition);
            if (Config.switchesNeedPower()) {
                // a short beep down when the switch goes dark, up when it comes back
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS,
                        0.6f, wasPowered ? 0.5f : 1.4f);
            }
            sync();
        }
        if ((level.getGameTime() + worldPosition.asLong()) % SAMPLE_TICKS == 0) {
            sample();
        }
    }

    private void sample() {
        int newActivity = ACTIVITY_IDLE;
        int newLoad = -1;
        if (isPowered()) {
            ComputeGrid grid = ComputeNets.grid(level);
            NetNode node = grid.nodeAt(worldPosition);
            if (node != null && node.net() != null) {
                NetStatus status = node.net().status(grid.limits());
                boolean limited = status.capacity() != Long.MAX_VALUE && status.capacity() > 0;
                double ratio = limited ? (double) status.delivered() / status.capacity() : 0;
                newLoad = limited ? (int) Math.round(Math.min(1.0, ratio) * 100) : -1;
                if (status.delivered() > 0) {
                    newActivity = !limited ? 2 : ratio >= 0.95 ? ACTIVITY_SATURATED : ratio >= 0.6 ? 3 : ratio >= 0.2 ? 2 : 1;
                }
            }
        }
        loadPercent = newLoad;
        if (newActivity != activity) {
            activity = newActivity;
            sync();
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
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
        if (tag.contains("activity")) {
            activity = tag.getInt("activity");
        }
    }

    /** What the client needs for the LEDs: power and activity. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("powered", isPowered());
        tag.putInt("activity", activity);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
