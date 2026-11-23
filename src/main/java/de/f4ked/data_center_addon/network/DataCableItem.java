package de.f4ked.data_center_addon.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Shows a cable's speed, capacity and reach. */
public class DataCableItem extends BlockItem {
    private final CableType type;

    public DataCableItem(DataCableBlock block, Properties properties) {
        super(block, properties);
        this.type = block.type();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.data_center_addon.cable.speed",
                NetworkText.speed(type.gbitPerSecond), NetworkText.number(type.capacity())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.data_center_addon.cable.reach", NetworkText.number(type.reach),
                NetworkText.speed(type.gbitPerSecond / CableType.OVERLENGTH_DIVISOR)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(type.medium == CableType.Medium.FIBRE
                ? "tooltip.data_center_addon.cable.fibre" : "tooltip.data_center_addon.cable.copper").withStyle(ChatFormatting.DARK_GRAY));
    }
}
