package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.shop.EarlyServers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Data Center's complete servers of 1998 to 2008 (new and used) are no longer registered: the add-on's
 * pre-built servers cover those years too.
 */
@Mixin(value = EarlyServers.class, remap = false)
public abstract class EarlyServersMixin {
    @Inject(method = "registerAll", at = @At("HEAD"), cancellable = true, require = 0)
    private static void data_center_addon$noOriginals(CallbackInfo ci) {
        ci.cancel();
    }
}
