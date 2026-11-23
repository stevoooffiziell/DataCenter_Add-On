package de.f4ked.data_center_addon.network;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.cable.CableBlock;
import com.example.datacenter.cable.SwitchBlock;
import com.example.datacenter.hosting.HostingRackBlock;
import com.example.datacenter.net.CableGrade;
import com.example.datacenter.net.ComputeNets;
import com.example.datacenter.net.NetConductor;
import com.example.datacenter.net.NetMachine;
import com.example.datacenter.rack.RackBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * A data cable of one {@link CableType}. It joins Data Center's compute network like Data Center's own
 * cables, but as a plain connector: its real capacity and reach are applied by {@link NetworkTopology}.
 */
public class DataCableBlock extends PipeBlock implements NetConductor {
    private final CableType type;

    public DataCableBlock(CableType type, Properties properties) {
        super(0.15f, properties);
        this.type = type;
        BlockState state = this.stateDefinition.any();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), false);
        }
        registerDefaultState(state);
    }

    public CableType type() {
        return type;
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return simpleCodec(properties -> new DataCableBlock(type, properties));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectsTo(level, neighborPos));
    }

    private static BlockState connections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectsTo(level, pos.relative(direction)));
        }
        return state;
    }

    /** Same rule as Data Center's cables, so both kinds look joined wherever the network joins them. */
    static boolean connectsTo(BlockGetter level, BlockPos pos) {
        BlockState neighbor = level.getBlockState(pos);
        Block block = neighbor.getBlock();
        if (block instanceof CableBlock || block instanceof SwitchBlock || block instanceof HostingRackBlock
                || neighbor.is(DataCenterApi.CABLE_CONNECTABLE)) {
            return true;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof RackBlockEntity || be instanceof NetMachine;
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
