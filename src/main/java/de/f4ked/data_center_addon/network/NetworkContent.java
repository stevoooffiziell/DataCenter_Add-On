package de.f4ked.data_center_addon.network;

import com.example.datacenter.shop.ShopCatalog;
import com.example.datacenter.shop.ShopCategory;
import com.example.datacenter.shop.ShopEntry;
import de.f4ked.data_center_addon.Data_center_addon;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Blocks, items and Shop entries of the add-on's network cables and switches. */
public final class NetworkContent {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Data_center_addon.MODID);

    public static final Map<CableType, DeferredBlock<DataCableBlock>> CABLES = new EnumMap<>(CableType.class);
    public static final Map<CableType, DeferredItem<DataCableItem>> CABLE_ITEMS = new EnumMap<>(CableType.class);
    public static final Map<SwitchModel, DeferredBlock<NetworkSwitchBlock>> SWITCHES = new EnumMap<>(SwitchModel.class);
    public static final Map<SwitchModel, DeferredItem<NetworkSwitchItem>> SWITCH_ITEMS = new EnumMap<>(SwitchModel.class);

    static {
        for (CableType type : CableType.values()) {
            DeferredBlock<DataCableBlock> block = Data_center_addon.BLOCKS.register(type.id(), () -> new DataCableBlock(type,
                    BlockBehaviour.Properties.of()
                            .mapColor(type.medium == CableType.Medium.FIBRE ? MapColor.COLOR_CYAN : MapColor.COLOR_LIGHT_GRAY)
                            .strength(1.0f)
                            .sound(SoundType.WOOL)
                            .noOcclusion()));
            CABLES.put(type, block);
            CABLE_ITEMS.put(type, Data_center_addon.ITEMS.register(type.id(),
                    () -> new DataCableItem(block.get(), new net.minecraft.world.item.Item.Properties())));
        }
        for (SwitchModel model : SwitchModel.values()) {
            DeferredBlock<NetworkSwitchBlock> block = Data_center_addon.BLOCKS.register(model.id(), () -> new NetworkSwitchBlock(model,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));
            SWITCHES.put(model, block);
            SWITCH_ITEMS.put(model, Data_center_addon.ITEMS.register(model.id(),
                    () -> new NetworkSwitchItem(block.get(), new net.minecraft.world.item.Item.Properties())));
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkSwitchBlockEntity>> SWITCH_ENTITY =
            BLOCK_ENTITIES.register("network_switch", () -> BlockEntityType.Builder.of(NetworkSwitchBlockEntity::new,
                    SWITCHES.values().stream().map(DeferredBlock::get).toArray(NetworkSwitchBlock[]::new)).build(null));

    private NetworkContent() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(NetworkContent::registerCapabilities);
        registerShopEntries();
    }

    /** Every cable and switch, in Shop order, for the creative tab. */
    public static List<ItemStack> creativeStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        CABLE_ITEMS.values().forEach(item -> stacks.add(item.get().getDefaultInstance()));
        SWITCH_ITEMS.values().forEach(item -> stacks.add(item.get().getDefaultInstance()));
        return Collections.unmodifiableList(stacks);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SWITCH_ENTITY.get(), (be, side) -> be.energy());
    }

    /**
     * Sold from their real release date at their launch price. Registered right after Data Center's
     * own entries (this mod loads after it), the same way on server and client.
     */
    private static void registerShopEntries() {
        for (CableType type : CableType.values()) {
            DeferredItem<DataCableItem> item = CABLE_ITEMS.get(type);
            ShopCatalog.register(new ShopEntry(ShopCategory.INFRASTRUCTURE, () -> new ItemStack(item.get()),
                    type.priceCents, type.releaseDate));
        }
        for (SwitchModel model : SwitchModel.values()) {
            DeferredItem<NetworkSwitchItem> item = SWITCH_ITEMS.get(model);
            ShopCatalog.register(new ShopEntry(ShopCategory.INFRASTRUCTURE, () -> new ItemStack(item.get()),
                    model.priceCents, model.releaseDate));
        }
    }
}
