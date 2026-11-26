package de.f4ked.data_center_addon.mixin.mekanism;

import de.f4ked.data_center_addon.compat.mekanism.WindUpgrades;
import mekanism.generators.common.tile.TileEntityWindGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Wind Generator's generation grows with its Speed Upgrades (also what its screen shows). */
@Mixin(value = TileEntityWindGenerator.class, remap = false)
public abstract class TileEntityWindGeneratorMixin {
    @Inject(method = "getCurrentGeneration", at = @At("RETURN"), cancellable = true)
    private void data_center_addon$speed(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(WindUpgrades.generation((TileEntityWindGenerator) (Object) this, cir.getReturnValueJ()));
    }
}
