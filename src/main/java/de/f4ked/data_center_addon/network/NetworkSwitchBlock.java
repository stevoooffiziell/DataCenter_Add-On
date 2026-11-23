package de.f4ked.data_center_addon.network;

import com.example.datacenter.net.CableGrade;
import com.example.datacenter.net.ComputeNets;
import com.example.datacenter.net.NetConductor;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** A network switch of one {@link SwitchModel}; joins Data Center's compute network as a connector. */
public class NetworkSwitchBlock extends HorizontalDirectionalBlock implements EntityBlock, NetConductor {
    /** Same footprint as Data Center's switch model: a 1U box. */
    private static final VoxelShape SHAPE_NS = Block.box(0, 0, 1, 16, 5, 15);
    private static final VoxelShape SHAPE_EW = Block.box(1, 0, 0, 15, 5, 16);

    private final SwitchModel model;

    public NetworkSwitchBlock(SwitchModel model, Properties properties) {
        super(properties);
        this.model = model;
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public SwitchModel model() {
        return model;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new NetworkSwitchBlock(model, properties));
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkSwitchBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != NetworkContent.SWITCH_ENTITY.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((NetworkSwitchBlockEntity) be).serverTick();
    }

    @Nullable
    @Override
    public CableGrade cableGrade() {
        return null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!state.is(oldState.getBlock())) {
            ComputeNets.conductorPlaced(level, pos, this);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            ComputeNets.conductorRemoved(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
