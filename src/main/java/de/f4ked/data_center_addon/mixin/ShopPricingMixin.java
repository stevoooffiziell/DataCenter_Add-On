package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.shop.ShopEntry;
import com.example.datacenter.shop.ShopPricing;
import de.f4ked.data_center_addon.shop.FluxPricing;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Flux tab's prices rise with the game date instead of falling like computer parts ({@link FluxPricing}). */
@Mixin(value = ShopPricing.class, remap = false)
public abstract class ShopPricingMixin {
    @Inject(method = "currentPriceUsdCents", at = @At("HEAD"), cancellable = true, require = 0)
    private static void data_center_addon$fluxPrice(ShopEntry entry, Level level, CallbackInfoReturnable<Long> cir) {
        if (FluxPricing.owns(entry)) {
            cir.setReturnValue(FluxPricing.priceUsdCents(entry, level));
        }
    }
}
