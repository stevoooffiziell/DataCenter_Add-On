package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.shop.ShopCatalog;
import com.example.datacenter.shop.ShopEntry;
import de.f4ked.data_center_addon.shop.AddonPrebuilts;
import de.f4ked.data_center_addon.shop.ShopExtras;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

/**
 * The add-on's changes to Data Center's shop catalog:
 * <ul>
 *   <li>Whenever the data-pack parts change (world start, joining a server, leaving it), the add-on's
 *       pre-built servers are composed anew from all parts and appended to the data-pack shop lines, on
 *       the server and on the client alike.</li>
 *   <li>The Mekanism transports of Data Center's arcade catalog are sold without it, and the retired
 *       split air conditioning units are sold again ({@link ShopExtras}).</li>
 * </ul>
 */
@Mixin(value = ShopCatalog.class, remap = false)
public abstract class ShopCatalogMixin {
    @ModifyVariable(method = "setPackEntries", at = @At("HEAD"), argsOnly = true, require = 0)
    private static List<ShopEntry> data_center_addon$prebuilts(List<ShopEntry> lines) {
        return AddonPrebuilts.withPrebuilts(lines);
    }

    @Redirect(method = "isAvailable(Lcom/example/datacenter/shop/ShopEntry;)Z", at = @At(value = "INVOKE",
            target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"), require = 0)
    private static boolean data_center_addon$arcade(Set<ShopEntry> arcadeOnly, Object entry) {
        return arcadeOnly.contains(entry) && !ShopExtras.unlocksArcade((ShopEntry) entry);
    }

    @Inject(method = "retired", at = @At("HEAD"), cancellable = true, require = 0)
    private static void data_center_addon$unretire(ShopEntry entry, CallbackInfoReturnable<Boolean> cir) {
        if (ShopExtras.unretires(entry)) {
            cir.setReturnValue(false);
        }
    }
}
