package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.shop.ShopCategory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Adds the add-on's shop categories to Data Center's: {@code FLUX}, the partner tab of Flux Networks
 * ({@link de.f4ked.data_center_addon.shop.AddonCategories}). Data Center never switches over the categories,
 * so a constant at the end is safe.
 */
@Mixin(value = ShopCategory.class, remap = false)
public abstract class ShopCategoryMixin {
    @Shadow
    @Final
    @Mutable
    private static ShopCategory[] $VALUES;

    @Invoker("<init>")
    private static ShopCategory data_center_addon$create(String name, int ordinal, String translationKey, boolean vendor) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void data_center_addon$addCategories(CallbackInfo ci) {
        ShopCategory[] values = Arrays.copyOf($VALUES, $VALUES.length + 1);
        values[values.length - 1] = data_center_addon$create("FLUX", values.length - 1, "shop.vendor.flux", true);
        $VALUES = values;
    }
}
