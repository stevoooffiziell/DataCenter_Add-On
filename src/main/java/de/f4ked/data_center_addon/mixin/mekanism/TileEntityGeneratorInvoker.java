package de.f4ked.data_center_addon.mixin.mekanism;

import mekanism.generators.common.tile.TileEntityGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Sets a Mekanism generator's output limit (it stores twice the value given). */
@Mixin(value = TileEntityGenerator.class, remap = false)
public interface TileEntityGeneratorInvoker {
    @Invoker("updateMaxOutputRaw")
    void data_center_addon$updateMaxOutputRaw(long maxOutput);
}
