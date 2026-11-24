package de.f4ked.data_center_addon.jade;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.hosting.HostingMode;
import com.example.datacenter.hosting.HostingProduct;
import com.example.datacenter.hosting.HostingRackBlockEntity;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.hosting.HostingManagerService;
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

/** Hosting rack: product, customers against capacity, income per day and what keeps it from earning. */
public final class HostingRackProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final HostingRackProvider INSTANCE = new HostingRackProvider();
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "hosting_rack");
    private static final String[] STATUS = {"ok", "no_panel", "no_hypervisor", "moving", "no_server", "no_game_os"};

    private HostingRackProvider() {
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof HostingRackBlockEntity rack) || !(accessor.getLevel() instanceof ServerLevel level)
                || !DataCenterApi.mayControl(level, accessor.getPosition(), accessor.getPlayer())) {
            return;
        }
        HostingProduct product = rack.getProduct();
        long centsPerDay = HostingManagerService.centsPerDay(rack);
        CompoundTag tag = new CompoundTag();
        tag.putInt("product", product == null ? -1 : product.ordinal());
        tag.putDouble("customers", rack.getModeCustomers());
        tag.putDouble("capacity", rack.getModeCapacity());
        tag.putLong("cents", centsPerDay);
        tag.putInt("status", rack.getModeStatus());
        tag.putBoolean("licence", HostingManagerService.licenceBound(product, rack, centsPerDay));
        data.put("addonRack", tag);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData().getCompound("addonRack");
        if (tag.isEmpty()) {
            return;
        }
        int product = tag.getInt("product");
        Component name = product >= 0 && product < HostingProduct.values().length
                ? Component.translatable(HostingProduct.values()[product].translationKey())
                : Component.translatable(HostingMode.DEDICATED.translationKey());
        if (product >= 0) {
            tooltip.add(Component.translatable("data_center_addon.jade.rack.customers", name,
                    GuiText.compactNumber(Math.round(tag.getDouble("customers"))),
                    GuiText.compactNumber(Math.round(tag.getDouble("capacity")))));
        } else {
            tooltip.add(name);
        }
        tooltip.add(Component.translatable("data_center_addon.jade.per_day", GuiText.compactUsd(tag.getLong("cents")))
                .withStyle(ChatFormatting.GREEN));
        int status = tag.getInt("status");
        if (status > 0 && status < STATUS.length) {
            tooltip.add(Component.literal("⚠ ").append(Component.translatable("data_center_addon.hosting.status." + STATUS[status]))
                    .withStyle(ChatFormatting.GOLD));
        } else if (tag.getBoolean("licence")) {
            tooltip.add(Component.literal("⚠ ").append(Component.translatable("data_center_addon.hosting.racks.licence_hint"))
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
