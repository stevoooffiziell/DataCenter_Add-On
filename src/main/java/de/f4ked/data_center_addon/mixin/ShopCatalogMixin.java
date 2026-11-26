package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.shop.ShopCatalog;
import com.example.datacenter.shop.ShopEntry;
import de.f4ked.data_center_addon.shop.AddonPrebuilts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * Whenever the data-pack parts change (world start, joining a server, leaving it), the add-on's pre-built
 * servers are composed anew from all parts and appended to the data-pack shop lines, on the server and on
 * the client alike.
 */
@Mixin(value = ShopCatalog.class, remap = false)
public abstract class ShopCatalogMixin {
    @ModifyVariable(method = "setPackEntries", at = @At("HEAD"), argsOnly = true, require = 0)
    private static List<ShopEntry> data_center_addon$prebuilts(List<ShopEntry> lines) {
        return AddonPrebuilts.withPrebuilts(lines);
    }
}
