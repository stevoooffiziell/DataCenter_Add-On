package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.catalog.PartPacks;
import com.example.datacenter.news.NewsEvents;
import com.example.datacenter.shop.ShopCatalog;
import com.example.datacenter.shop.ShopEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Data Center announces every shop part in chat on its release date ("New in the Shop: ..."), also
 * parts a data pack hides from the shop. When the add-on hides the original parts, those
 * announcements would advertise parts nobody can buy.
 * <p>
 * {@code build} skips parts that are on sale from the world's start; hidden parts are skipped the
 * same way. The news list is rebuilt whenever the part packs are re-applied, so it follows the config.
 */
@Mixin(value = NewsEvents.class, remap = false)
public abstract class NewsEventsMixin {
    @Redirect(
            method = "build",
            at = @At(value = "INVOKE", target = "Lcom/example/datacenter/shop/ShopCatalog;fromWorldStart(Lcom/example/datacenter/shop/ShopEntry;)Z"),
            require = 0)
    private static boolean data_center_addon$skipHiddenParts(ShopEntry entry) {
        return ShopCatalog.fromWorldStart(entry) || PartPacks.hidden(entry);
    }
}
