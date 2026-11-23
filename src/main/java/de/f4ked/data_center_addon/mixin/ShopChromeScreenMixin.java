package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.client.screen.ShopChromeScreen;
import de.f4ked.data_center_addon.client.ShopWidth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Widens the shop's content pane by {@link ShopWidth#extra()}. Data Center compiled its content
 * width and height (both 250) in as constants, so only the occurrences that are widths are changed:
 * in {@code renderBg} the x/y pairs alternate (ordinals 0 and 2 are x).
 * <p>
 * {@code require = 0}: if a Data Center update changes these methods, the shop keeps its own width
 * instead of crashing the game.
 */
@Mixin(value = ShopChromeScreen.class, remap = false)
public abstract class ShopChromeScreenMixin {
    @ModifyConstant(method = "renderBg",
            constant = {@Constant(intValue = 250, ordinal = 0), @Constant(intValue = 250, ordinal = 2)},
            require = 0)
    private int data_center_addon$widenPane(int width) {
        return width + ShopWidth.extra();
    }
}
