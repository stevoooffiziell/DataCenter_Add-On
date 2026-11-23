package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.client.screen.ShopScreen;
import de.f4ked.data_center_addon.client.ShopWidth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Widens the Shop page with {@link ShopWidth#extra()}: the content width (250) and the widths
 * derived from it (244 chip rows, 242 list rows, 234 wrapped text). In {@code contentClicked} the
 * second 250 is the list height and stays as it is.
 */
@Mixin(value = ShopScreen.class, remap = false)
public abstract class ShopScreenMixin {
    @ModifyConstant(method = {"layoutSearchBox", "renderContent", "renderHoveredRowTooltip", "mouseScrolled"},
            constant = @Constant(intValue = 250), require = 0)
    private int data_center_addon$contentWidth(int width) {
        return width + ShopWidth.extra();
    }

    @ModifyConstant(method = "contentClicked",
            constant = {@Constant(intValue = 250, ordinal = 0), @Constant(intValue = 250, ordinal = 2)},
            require = 0)
    private int data_center_addon$clickWidth(int width) {
        return width + ShopWidth.extra();
    }

    @ModifyConstant(method = {"facilityChipLayout", "tabPad", "sortPad"},
            constant = @Constant(intValue = 244), require = 0)
    private int data_center_addon$chipRowWidth(int width) {
        return width + ShopWidth.extra();
    }

    @ModifyConstant(method = "renderRow", constant = @Constant(intValue = 242), require = 0)
    private int data_center_addon$rowWidth(int width) {
        return width + ShopWidth.extra();
    }

    @ModifyConstant(method = "renderContent", constant = @Constant(intValue = 234), require = 0)
    private int data_center_addon$textWidth(int width) {
        return width + ShopWidth.extra();
    }
}
