package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.net.ComputeGrid;
import com.example.datacenter.net.NetNode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Package-private parts of Data Center's network nodes, for {@code NetworkTopology}. */
@Mixin(value = NetNode.class, remap = false)
public interface NetNodeAccessor {
    @Accessor("grid")
    ComputeGrid data_center_addon$grid();

    /** Extra block positions of a multi-block device. */
    @Accessor("ports")
    BlockPos[] data_center_addon$ports();
}
