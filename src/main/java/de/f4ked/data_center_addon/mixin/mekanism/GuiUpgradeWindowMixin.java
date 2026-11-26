package de.f4ked.data_center_addon.mixin.mekanism;

import de.f4ked.data_center_addon.compat.mekanism.WindUpgrades;
import mekanism.api.Upgrade;
import mekanism.client.gui.element.window.GuiUpgradeWindow;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The upgrade window shows the Wind Generator's own limit ("4/4" instead of "4/8"). */
@Mixin(value = GuiUpgradeWindow.class, remap = false)
public abstract class GuiUpgradeWindowMixin {
    @Shadow
    @Final
    private TileEntityMekanism tile;

    @Redirect(method = "renderForeground", at = @At(value = "INVOKE", target = "Lmekanism/api/Upgrade;getMax()I"))
    private int data_center_addon$max(Upgrade upgrade) {
        return WindUpgrades.max(tile, upgrade);
    }
}
