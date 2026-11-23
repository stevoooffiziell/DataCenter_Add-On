package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.net.ComputeGrid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Counts every block placed into or removed from the grid; {@code NetworkTopology} caches by it. */
@Mixin(value = ComputeGrid.class, remap = false)
public interface ComputeGridInvoker {
    @Invoker("structureVersion")
    int data_center_addon$structureVersion();
}
