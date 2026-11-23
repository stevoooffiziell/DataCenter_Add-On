package de.f4ked.data_center_addon.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets {@code ShopWidth} resize Data Center's shop window. */
@Mixin(value = AbstractContainerScreen.class, remap = false)
public interface AbstractContainerScreenAccessor {
    @Accessor("imageWidth")
    void data_center_addon$setImageWidth(int width);
}
