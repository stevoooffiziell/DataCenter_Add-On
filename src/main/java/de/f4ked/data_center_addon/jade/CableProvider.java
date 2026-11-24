package de.f4ked.data_center_addon.jade;

import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.network.CableType;
import de.f4ked.data_center_addon.network.DataCableBlock;
import de.f4ked.data_center_addon.network.NetworkText;
import de.f4ked.data_center_addon.network.NetworkTopology;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Data cable: its speed and reach, and for the run it belongs to (the stretch between switches) the
 * length against the reach, the slowest cable and the speed the run actually gets.
 */
public final class CableProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final CableProvider INSTANCE = new CableProvider();
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "data_cable");

    private CableProvider() {
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) {
            return;
        }
        NetworkTopology.Run run = NetworkTopology.runAt(level, accessor.getPosition());
        if (run == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("length", run.length());
        tag.putInt("reach", run.reach());
        tag.putBoolean("overlength", run.overlength());
        tag.putDouble("gbit", run.capacity() == Long.MAX_VALUE ? -1 : (double) run.capacity() / CableType.COMPUTE_PER_GBIT);
        tag.putInt("weakest", run.weakest() == null ? -1 : run.weakest().ordinal());
        data.put("addonCable", tag);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlock() instanceof DataCableBlock block)) {
            return;
        }
        CableType type = block.type();
        tooltip.add(Component.translatable("data_center_addon.jade.cable.specs", NetworkText.speed(type.gbitPerSecond),
                NetworkText.number(type.reach)).withStyle(ChatFormatting.GRAY));
        CompoundTag tag = accessor.getServerData().getCompound("addonCable");
        if (tag.isEmpty()) {
            return;
        }
        int length = tag.getInt("length");
        int reach = tag.getInt("reach");
        boolean overlength = tag.getBoolean("overlength");
        Component run = reach == Integer.MAX_VALUE
                ? Component.translatable("data_center_addon.jade.cable.run_unlimited", NetworkText.number(length))
                : Component.translatable("data_center_addon.jade.cable.run", NetworkText.number(length), NetworkText.number(reach));
        tooltip.add(run.copy().withStyle(overlength ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        double gbit = tag.getDouble("gbit");
        int weakest = tag.getInt("weakest");
        if (weakest >= 0 && weakest < CableType.values().length && weakest != type.ordinal()) {
            tooltip.add(Component.translatable("data_center_addon.jade.cable.weakest",
                    Component.translatable("block.data_center_addon." + CableType.values()[weakest].id())).withStyle(ChatFormatting.GRAY));
        }
        if (overlength) {
            tooltip.add(Component.literal("⚠ ").append(Component.translatable("data_center_addon.jade.cable.overlength",
                    gbit >= 0 ? NetworkText.speed(gbit) : "?")).withStyle(ChatFormatting.GOLD));
        } else if (gbit >= 0) {
            tooltip.add(Component.translatable("data_center_addon.jade.cable.speed", NetworkText.speed(gbit)).withStyle(ChatFormatting.GREEN));
        }
    }
}
