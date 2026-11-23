package de.f4ked.data_center_addon.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Shows a switch's switching capacity, power draw and what it does to the cable runs. */
public class NetworkSwitchItem extends BlockItem {
    private final SwitchModel model;

    public NetworkSwitchItem(NetworkSwitchBlock block, Properties properties) {
        super(block, properties);
        this.model = block.model();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.data_center_addon.switch.backplane",
                NetworkText.speed(model.backplaneGbit), NetworkText.number(model.backplane())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.data_center_addon.switch.power", model.watts).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.data_center_addon.switch.runs").withStyle(ChatFormatting.DARK_GRAY));
    }
}
