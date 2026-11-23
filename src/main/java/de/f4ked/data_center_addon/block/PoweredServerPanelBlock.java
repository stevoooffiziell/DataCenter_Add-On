package de.f4ked.data_center_addon.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Powered Server Room Panel.
 * <p>
 * Data Center treats every cable (Mekanism, Powah, Pipez, its own, ...) as {@code datacenter:room_passable}:
 * air flows through it, so a cable in the wall of a server room is a hole and the room is not sealed.
 * This block is a full solid cube (and not in that tag), so it seals the room like the normal
 * Server Room Panel. It has an energy port on each end of its axis (the axis is set by the face you
 * place it against, like a log) and passes FE between them without storing any.
 * <p>
 * Panels with the same axis can be chained to go through walls thicker than one block.
 */
public class PoweredServerPanelBlock extends RotatedPillarBlock {
    public static final MapCodec<PoweredServerPanelBlock> CODEC = simpleCodec(PoweredServerPanelBlock::new);

    public PoweredServerPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return CODEC;
    }

    /**
     * The energy capability for one face: only the two port faces (the ends of the axis) have one.
     */
    @Nullable
    public static IEnergyStorage energyFor(Level level, BlockPos pos, BlockState state, @Nullable Direction side) {
        if (side == null || side.getAxis() != state.getValue(AXIS)) {
            return null;
        }
        return new PassThroughEnergyStorage(level, pos, side);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // The ports depend on the axis, so neighbours that cached our capability must look again.
        if (!oldState.is(state.getBlock()) || oldState.getValue(AXIS) != state.getValue(AXIS)) {
            level.invalidateCapabilities(pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!newState.is(state.getBlock())) {
            level.invalidateCapabilities(pos);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.data_center_addon.powered_server_panel").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.data_center_addon.powered_server_panel.ports").withStyle(ChatFormatting.DARK_GRAY));
    }
}
