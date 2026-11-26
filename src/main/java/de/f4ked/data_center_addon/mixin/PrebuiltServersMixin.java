package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.shop.PrebuiltServers;
import com.example.datacenter.shop.ShopEntry;
import de.f4ked.data_center_addon.shop.AddonPrebuilts;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.LocalDate;

/**
 * Data Center's generated pre-built servers (budget, performance, high-end and the compute, hosting and
 * mining builds) are no longer registered; the add-on's own ({@link AddonPrebuilts}) take their place, and
 * Data Center asks the add-on whether one of those is on sale and what it costs.
 */
@Mixin(value = PrebuiltServers.class, remap = false)
public abstract class PrebuiltServersMixin {
    @Inject(method = "registerAll", at = @At("HEAD"), cancellable = true, require = 0)
    private static void data_center_addon$noOriginals(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "onSale", at = @At("HEAD"), cancellable = true, require = 0)
    private static void data_center_addon$onSale(ShopEntry entry, LocalDate today, CallbackInfoReturnable<Boolean> cir) {
        if (AddonPrebuilts.owns(entry)) {
            cir.setReturnValue(AddonPrebuilts.onSale(entry, today));
        }
    }

    @Inject(method = "priceUsdCents", at = @At("HEAD"), cancellable = true, require = 0)
    private static void data_center_addon$price(ShopEntry entry, Level level, CallbackInfoReturnable<Long> cir) {
        if (AddonPrebuilts.owns(entry)) {
            cir.setReturnValue(AddonPrebuilts.priceUsdCents(entry, level));
        }
    }
}
