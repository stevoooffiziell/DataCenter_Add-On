package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.shop.ShopCategory;
import de.f4ked.data_center_addon.shop.AddonCategories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Arrays;

/** Puts the add-on's partner tabs (Flux) into Data Center's "Partners" group, after its own. */
@Mixin(value = com.example.datacenter.shop.ShopGroup.class, remap = false)
public abstract class ShopGroupMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/List;of([Ljava/lang/Object;)Ljava/util/List;"))
    private static Object[] data_center_addon$partners(Object[] categories) {
        if (!Arrays.asList(categories).contains(ShopCategory.MEKANISM)) {
            return categories;
        }
        Object[] more = Arrays.copyOf(categories, categories.length + 1, ShopCategory[].class);
        more[more.length - 1] = AddonCategories.flux();
        return more;
    }
}
