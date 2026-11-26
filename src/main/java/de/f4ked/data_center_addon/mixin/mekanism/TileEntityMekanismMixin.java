package de.f4ked.data_center_addon.mixin.mekanism;

import de.f4ked.data_center_addon.compat.mekanism.WindUpgrades;
import mekanism.api.Upgrade;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.generators.common.tile.TileEntityWindGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** When the Speed Upgrades of a Wind Generator change, its output limit follows them. */
@Mixin(value = TileEntityMekanism.class, remap = false)
public abstract class TileEntityMekanismMixin {
    @Inject(method = "recalculateUpgrades", at = @At("TAIL"))
    private void data_center_addon$windOutput(Upgrade upgrade, CallbackInfo ci) {
        if (upgrade == Upgrade.SPEED && (Object) this instanceof TileEntityWindGenerator wind) {
            WindUpgrades.updateMaxOutput(wind);
        }
    }
}
