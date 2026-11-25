package de.f4ked.data_center_addon.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets {@code ShopWidth} resize Data Center's shop window; {@code TechScreens} reads where the labels go. */
@Mixin(value = AbstractContainerScreen.class, remap = false)
public interface AbstractContainerScreenAccessor {
    @Accessor("imageWidth")
    void data_center_addon$setImageWidth(int width);

    @Accessor("titleLabelX")
    int data_center_addon$titleLabelX();

    @Accessor("titleLabelY")
    int data_center_addon$titleLabelY();

    @Accessor("inventoryLabelX")
    int data_center_addon$inventoryLabelX();

    @Accessor("inventoryLabelY")
    int data_center_addon$inventoryLabelY();

    @Accessor("playerInventoryTitle")
    Component data_center_addon$playerInventoryTitle();
}
