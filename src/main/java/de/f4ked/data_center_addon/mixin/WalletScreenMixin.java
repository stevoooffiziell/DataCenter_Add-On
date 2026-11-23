package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.client.screen.WalletScreen;
import de.f4ked.data_center_addon.client.ShopWidth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Widens the Wallet page with {@link ShopWidth#extra()} (every 250 here is the content width). */
@Mixin(value = WalletScreen.class, remap = false)
public abstract class WalletScreenMixin {
    @ModifyConstant(method = "renderContent", constant = @Constant(intValue = 250), require = 0)
    private int data_center_addon$contentWidth(int width) {
        return width + ShopWidth.extra();
    }

    @ModifyConstant(method = "renderContent", constant = @Constant(intValue = 238), require = 0)
    private int data_center_addon$textWidth(int width) {
        return width + ShopWidth.extra();
    }
}
