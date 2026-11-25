package de.f4ked.data_center_addon.industry;

import com.example.datacenter.mining.MiningEra;
import com.example.datacenter.shop.ShopCatalog;
import com.example.datacenter.shop.ShopCategory;
import com.example.datacenter.shop.ShopEntry;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.industry.machine.FactoryMachineBlock;
import de.f4ked.data_center_addon.industry.machine.FactoryMachineBlockEntity;
import de.f4ked.data_center_addon.industry.machine.FactoryMachineMenu;
import de.f4ked.data_center_addon.industry.machine.MachineKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Blocks, items, menus and Shop entries of the add-on's industry: buying, processing and selling goods. */
public final class IndustryContent {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Data_center_addon.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Data_center_addon.MODID);

    // ------------------------------------------------------------------ items

    /** Plastic pellets: the raw material of every plastic part. */
    public static final DeferredItem<Item> PLASTIC_PELLETS = Data_center_addon.ITEMS.registerSimpleItem("plastic_pellets");
    /** Silicon, made from quartz and coal in the smelter: the base of wafers and chips. */
    public static final DeferredItem<Item> SILICON = Data_center_addon.ITEMS.registerSimpleItem("silicon");

    // ------------------------------------------------------------------ factory machines

    public static final Map<MachineKind, DeferredBlock<FactoryMachineBlock>> MACHINES = new EnumMap<>(MachineKind.class);
    public static final Map<MachineKind, DeferredItem<BlockItem>> MACHINE_ITEMS = new EnumMap<>(MachineKind.class);

    static {
        for (MachineKind kind : MachineKind.values()) {
            DeferredBlock<FactoryMachineBlock> block = Data_center_addon.BLOCKS.register(kind.id(), () -> new FactoryMachineBlock(kind,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f, 6.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .lightLevel(FactoryMachineBlock::lightLevel)
                            .noOcclusion()));
            MACHINES.put(kind, block);
            MACHINE_ITEMS.put(kind, Data_center_addon.ITEMS.registerSimpleBlockItem(block));
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FactoryMachineBlockEntity>> MACHINE_ENTITY =
            BLOCK_ENTITIES.register("factory_machine", () -> BlockEntityType.Builder.of(FactoryMachineBlockEntity::new,
                    MACHINES.values().stream().map(DeferredBlock::get).toArray(FactoryMachineBlock[]::new)).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<FactoryMachineMenu>> MACHINE_MENU = MENUS.register(
            "factory_machine",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new FactoryMachineMenu(id, inventory, buf.readBlockPos())));

    // ------------------------------------------------------------------ purchase terminal

    public static final DeferredBlock<PurchaseTerminalBlock> PURCHASE_TERMINAL = Data_center_addon.BLOCKS.registerBlock(
            "purchase_terminal", PurchaseTerminalBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion());
    public static final DeferredItem<BlockItem> PURCHASE_TERMINAL_ITEM = Data_center_addon.ITEMS.registerSimpleBlockItem(PURCHASE_TERMINAL);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PurchaseTerminalBlockEntity>> PURCHASE_TERMINAL_ENTITY =
            BLOCK_ENTITIES.register("purchase_terminal",
                    () -> BlockEntityType.Builder.of(PurchaseTerminalBlockEntity::new, PURCHASE_TERMINAL.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<PurchaseTerminalMenu>> PURCHASE_TERMINAL_MENU = MENUS.register(
            "purchase_terminal",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new PurchaseTerminalMenu(id, inventory, buf.readBlockPos())));

    /** Shop price of the Purchase Terminal in US cents. */
    private static final long PURCHASE_TERMINAL_CENTS = 4_000_00L;

    private IndustryContent() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        modEventBus.addListener(IndustryContent::registerCapabilities);
        ShopCatalog.register(new ShopEntry(ShopCategory.INFRASTRUCTURE, () -> new ItemStack(PURCHASE_TERMINAL_ITEM.get()),
                PURCHASE_TERMINAL_CENTS, MiningEra.FROM_WORLD_START));
        for (MachineKind kind : MachineKind.values()) {
            DeferredItem<BlockItem> item = MACHINE_ITEMS.get(kind);
            ShopCatalog.register(new ShopEntry(ShopCategory.INFRASTRUCTURE, () -> new ItemStack(item.get()), kind.priceCents,
                    MiningEra.FROM_WORLD_START));
        }
    }

    /** For the creative tab. */
    public static List<ItemStack> creativeStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(PURCHASE_TERMINAL_ITEM.get().getDefaultInstance());
        MACHINE_ITEMS.values().forEach(item -> stacks.add(item.get().getDefaultInstance()));
        stacks.add(PLASTIC_PELLETS.get().getDefaultInstance());
        stacks.add(SILICON.get().getDefaultInstance());
        return List.copyOf(stacks);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // hoppers and pipes may take the goods, but put nothing in
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PURCHASE_TERMINAL_ENTITY.get(), (be, side) -> be.output());
        // machines: inputs in, outputs out, power from every side
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MACHINE_ENTITY.get(), (be, side) -> be.automation());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MACHINE_ENTITY.get(), (be, side) -> be.energy());
    }
}
