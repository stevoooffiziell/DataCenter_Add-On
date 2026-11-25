package de.f4ked.data_center_addon.industry.manager;

import de.f4ked.data_center_addon.industry.IndustryPayloads;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Factory Manager: a management terminal for the factory around it (machines, budget, purchases,
 * sales and margins of the last 30 days), separate from the Hosting Manager.
 */
public class FactoryManagerBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<FactoryManagerBlock> CODEC = simpleCodec(FactoryManagerBlock::new);
    private static final VoxelShape SHAPE_NS = Block.box(1, 0, 4, 15, 16, 12);
    private static final VoxelShape SHAPE_EW = Block.box(4, 0, 1, 12, 16, 15);

    public FactoryManagerBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new FactoryManagerMenu(id, inventory, pos),
                    Component.translatable("block.data_center_addon.factory_manager")), buf -> buf.writeBlockPos(pos));
            IndustryPayloads.sendFactorySnapshot(serverPlayer, serverLevel, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
