package de.f4ked.data_center_addon.industry.chipfab;

import com.mojang.serialization.MapCodec;
import de.f4ked.data_center_addon.industry.IndustryContent;
import de.f4ked.data_center_addon.industry.machine.FactoryMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** The controller of the chip production line ({@link ChipFabBlockEntity}); faces the player, {@code active} while working. */
public class ChipFabControllerBlock extends BaseEntityBlock {
    public static final MapCodec<ChipFabControllerBlock> CODEC = simpleCodec(ChipFabControllerBlock::new);

    public ChipFabControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(FactoryMachineBlock.ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING, FactoryMachineBlock.ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChipFabBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, IndustryContent.CHIP_FAB_ENTITY.get(), ChipFabBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ChipFabBlockEntity be) {
            serverPlayer.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ChipFabBlockEntity be) {
            for (int i = 0; i < be.inventory().getSlots(); i++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), be.inventory().getStackInSlot(i));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
