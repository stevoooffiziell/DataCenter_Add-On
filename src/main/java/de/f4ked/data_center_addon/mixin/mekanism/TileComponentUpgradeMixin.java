package de.f4ked.data_center_addon.mixin.mekanism;

import de.f4ked.data_center_addon.compat.mekanism.WindUpgrades;
import mekanism.api.Upgrade;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentUpgrade;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Installs at most {@link WindUpgrades#max} upgrades: fewer Speed Upgrades in the Wind Generator. */
@Mixin(value = TileComponentUpgrade.class, remap = false)
public abstract class TileComponentUpgradeMixin {
    @Shadow
    @Final
    private TileEntityMekanism tile;

    @Redirect(method = {"tickServer", "addUpgrades(Lmekanism/api/Upgrade;II)I"},
            at = @At(value = "INVOKE", target = "Lmekanism/api/Upgrade;getMax()I"))
    private int data_center_addon$max(Upgrade upgrade) {
        return WindUpgrades.max(tile, upgrade);
    }
}
