package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.shop.ShopCatalog;
import com.example.datacenter.shop.ShopEntry;
import com.example.datacenter.shop.ShopMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * The shop buys an entry without a part id by its index in {@link ShopCatalog#entries()}, but Data Center
 * only accepts indices of its static entries. The add-on's pre-built servers come after the data-pack
 * lines, so the check covers the whole list (data-pack parts are still bought by id, and {@code buy}
 * still checks that the entry is on sale).
 */
@Mixin(value = ShopMenu.class, remap = false)
public abstract class ShopMenuMixin {
    @Redirect(method = "clickMenuButton", at = @At(value = "INVOKE",
            target = "Lcom/example/datacenter/shop/ShopCatalog;staticEntries()Ljava/util/List;"), require = 0)
    private List<ShopEntry> data_center_addon$allEntries() {
        return ShopCatalog.entries();
    }
}
