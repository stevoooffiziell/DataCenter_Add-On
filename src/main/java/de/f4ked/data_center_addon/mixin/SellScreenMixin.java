package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.client.screen.SellScreen;
import de.f4ked.data_center_addon.client.ShopWidth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Widens the Sell page with {@link ShopWidth#extra()} (every 250 here is the content width). */
@Mixin(value = SellScreen.class, remap = false)
public abstract class SellScreenMixin {
    @ModifyConstant(method = {"renderContent", "render", "contentClicked", "mouseScrolled"},
            constant = @Constant(intValue = 250), require = 0)
    private int data_center_addon$contentWidth(int width) {
        return width + ShopWidth.extra();
    }

    @ModifyConstant(method = "renderContent", constant = @Constant(intValue = 238), require = 0)
    private int data_center_addon$textWidth(int width) {
        return width + ShopWidth.extra();
    }

    @ModifyConstant(method = "renderRow", constant = @Constant(intValue = 242), require = 0)
    private int data_center_addon$rowWidth(int width) {
        return width + ShopWidth.extra();
    }
}
