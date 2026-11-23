package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.shop.ShopEntry;
import de.f4ked.data_center_addon.network.OriginalNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.LocalDate;

/** Takes Data Center's own switch out of the Shop when the config hides the original network parts. */
@Mixin(value = ShopEntry.class, remap = false)
public abstract class ShopEntryMixin {
    @Inject(method = "isAvailableOn", at = @At("HEAD"), cancellable = true, require = 0)
    private void data_center_addon$hideOriginalNetwork(LocalDate today, CallbackInfoReturnable<Boolean> cir) {
        if (OriginalNetwork.hidden((ShopEntry) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
