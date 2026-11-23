package de.f4ked.data_center_addon;

import com.mojang.serialization.MapCodec;
import de.f4ked.data_center_addon.block.PoweredServerPanelBlock;
import de.f4ked.data_center_addon.part.HideOriginalPartCondition;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Data_center_addon.MODID)
public class Data_center_addon {
    public static final String MODID = "data_center_addon";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /**
     * A Server Room Panel with an energy port on both ends. It is a full solid block, so it seals a
     * Data Center room like any wall block, and it passes FE straight through between its two ends.
     */
    public static final DeferredBlock<PoweredServerPanelBlock> POWERED_SERVER_PANEL = BLOCKS.registerBlock(
            "powered_server_panel",
            PoweredServerPanelBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> POWERED_SERVER_PANEL_ITEM = ITEMS.registerSimpleBlockItem(POWERED_SERVER_PANEL);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.data_center_addon"))
            .icon(() -> POWERED_SERVER_PANEL_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> output.accept(POWERED_SERVER_PANEL_ITEM.get()))
            .build());

    /** Data-pack conditions; {@code hide_original} hides Data Center's own parts when the config says so. */
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, MODID);
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<HideOriginalPartCondition>> HIDE_ORIGINAL =
            CONDITIONS.register("hide_original", () -> HideOriginalPartCondition.CODEC);

    public Data_center_addon(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        CONDITIONS.register(modEventBus);
        modEventBus.addListener(this::registerCapabilities);
        // config/data_center_addon-common.toml
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // No block entity needed: the capability is a stateless pass-through to the block at the other end.
        event.registerBlock(
                Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side) -> PoweredServerPanelBlock.energyFor(level, pos, state, side),
                POWERED_SERVER_PANEL.get());
    }
}
