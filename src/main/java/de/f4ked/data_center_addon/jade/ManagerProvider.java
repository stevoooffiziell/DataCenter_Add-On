package de.f4ked.data_center_addon.jade;

import com.example.datacenter.client.screen.GuiText;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.hosting.HostingManagerService;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Hosting Manager: the racks on its network, their customers and income, and the running contracts. */
public final class ManagerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final ManagerProvider INSTANCE = new ManagerProvider();
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "hosting_manager");

    private ManagerProvider() {
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level) || !(accessor.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        HostingSnapshot s = HostingManagerService.snapshot(level, accessor.getPosition(), player);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("connected", s.connected());
        tag.putInt("racks", s.racks().size());
        tag.putLong("customers", Math.round(s.totalCustomers()));
        tag.putLong("cents", s.incomeCentsPerDay());
        tag.putInt("licence", s.licenceBoundRacks());
        tag.putInt("contracts", s.activeContracts().size());
        tag.putInt("uncovered", (int) s.activeContracts().stream().filter(c -> !c.covered()).count());
        data.put("addonManager", tag);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData().getCompound("addonManager");
        if (tag.isEmpty()) {
            return;
        }
        if (!tag.getBoolean("connected")) {
            tooltip.add(Component.translatable("data_center_addon.hosting.not_connected").withStyle(ChatFormatting.GOLD));
            return;
        }
        tooltip.add(Component.translatable("data_center_addon.jade.manager.racks", tag.getInt("racks"),
                GuiText.compactNumber(tag.getLong("customers"))));
        tooltip.add(Component.translatable("data_center_addon.jade.per_day", GuiText.compactUsd(tag.getLong("cents")))
                .withStyle(ChatFormatting.GREEN));
        int contracts = tag.getInt("contracts");
        if (contracts > 0) {
            int uncovered = tag.getInt("uncovered");
            tooltip.add(Component.translatable("data_center_addon.jade.manager.contracts", contracts)
                    .withStyle(uncovered > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            if (uncovered > 0) {
                tooltip.add(Component.literal("⚠ ").append(Component.translatable("data_center_addon.jade.manager.uncovered", uncovered))
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        if (tag.getInt("licence") > 0) {
            tooltip.add(Component.literal("⚠ ").append(Component.translatable("data_center_addon.jade.manager.licence", tag.getInt("licence")))
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
