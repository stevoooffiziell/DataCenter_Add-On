package de.f4ked.data_center_addon.jade;

import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.network.NetworkSwitchBlock;
import de.f4ked.data_center_addon.network.NetworkSwitchBlockEntity;
import de.f4ked.data_center_addon.network.NetworkText;
import de.f4ked.data_center_addon.network.SwitchModel;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Network switch: backplane and wattage, power, and how much of its network's capacity is in use. */
public final class SwitchProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final SwitchProvider INSTANCE = new SwitchProvider();
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "network_switch");

    private SwitchProvider() {
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof NetworkSwitchBlockEntity be) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("powered", be.isPowered());
            tag.putInt("load", be.loadPercent());
            tag.putInt("activity", be.activity());
            data.put("addonSwitch", tag);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlock() instanceof NetworkSwitchBlock block)) {
            return;
        }
        SwitchModel model = block.model();
        tooltip.add(Component.translatable("data_center_addon.jade.switch.specs", NetworkText.speed(model.backplaneGbit), model.watts)
                .withStyle(ChatFormatting.GRAY));
        CompoundTag tag = accessor.getServerData().getCompound("addonSwitch");
        if (tag.isEmpty()) {
            return;
        }
        if (!tag.getBoolean("powered")) {
            tooltip.add(Component.literal("⚠ ").append(Component.translatable("data_center_addon.jade.switch.unpowered"))
                    .withStyle(ChatFormatting.RED));
            return;
        }
        int load = tag.getInt("load");
        if (load >= 0) {
            boolean saturated = tag.getInt("activity") >= NetworkSwitchBlockEntity.ACTIVITY_SATURATED;
            tooltip.add(Component.translatable("data_center_addon.jade.switch.load", load)
                    .withStyle(saturated ? ChatFormatting.GOLD : ChatFormatting.GREEN));
            if (saturated) {
                tooltip.add(Component.literal("⚠ ").append(Component.translatable("data_center_addon.jade.switch.saturated"))
                        .withStyle(ChatFormatting.GOLD));
            }
        } else {
            tooltip.add(Component.translatable(tag.getInt("activity") > 0
                    ? "data_center_addon.jade.switch.active" : "data_center_addon.jade.switch.idle").withStyle(ChatFormatting.GREEN));
        }
    }
}
