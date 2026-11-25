package de.f4ked.data_center_addon.industry;

import com.example.datacenter.mining.MiningEra;
import com.example.datacenter.shop.ShopCatalog;
import com.example.datacenter.shop.ShopCategory;
import com.example.datacenter.shop.ShopEntry;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.industry.chipfab.ChipFabBlockEntity;
import de.f4ked.data_center_addon.industry.chipfab.ChipFabControllerBlock;
import de.f4ked.data_center_addon.industry.chipfab.ChipFabMenu;
import de.f4ked.data_center_addon.industry.hardware.HardwareFabBlock;
import de.f4ked.data_center_addon.industry.hardware.HardwareFabBlockEntity;
import de.f4ked.data_center_addon.industry.hardware.HardwareFabMenu;
import de.f4ked.data_center_addon.industry.machine.FactoryMachineBlock;
import de.f4ked.data_center_addon.industry.manager.FactoryManagerBlock;
import de.f4ked.data_center_addon.industry.manager.FactoryManagerMenu;
import de.f4ked.data_center_addon.industry.machine.FactoryMachineBlockEntity;
import de.f4ked.data_center_addon.industry.machine.FactoryMachineMenu;
import de.f4ked.data_center_addon.industry.machine.MachineKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
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
    /** Copper foil, rolled from copper ingots: the conductor layer of circuit boards and capacitors. */
    public static final DeferredItem<Item> COPPER_FOIL = Data_center_addon.ITEMS.registerSimpleItem("copper_foil");
    /** Gold contacts, rolled from gold ingots: for connectors and chips. */
    public static final DeferredItem<Item> GOLD_CONTACTS = Data_center_addon.ITEMS.registerSimpleItem("gold_contacts");
    /** Copper wire, drawn from copper ingots: the core of cables. */
    public static final DeferredItem<Item> COPPER_WIRE = Data_center_addon.ITEMS.registerSimpleItem("copper_wire");
    /** Plastic parts, pressed from pellets: housings, cable jackets, connector bodies. */
    public static final DeferredItem<Item> PLASTIC_PARTS = Data_center_addon.ITEMS.registerSimpleItem("plastic_parts");
    /** A circuit board blank: glass fibre laminated with copper foil, not yet etched. */
    public static final DeferredItem<Item> PCB_BLANK = Data_center_addon.ITEMS.registerSimpleItem("pcb_blank");
    /** A silicon wafer, sliced from silicon: the base of microchips. */
    public static final DeferredItem<Item> WAFER = Data_center_addon.ITEMS.registerSimpleItem("wafer");
    /** An etched circuit board: the base of every switch, server board and network card. */
    public static final DeferredItem<Item> CIRCUIT_BOARD = Data_center_addon.ITEMS.registerSimpleItem("circuit_board");
    /** Capacitors, rolled from copper foil in a plastic case. */
    public static final DeferredItem<Item> CAPACITOR = Data_center_addon.ITEMS.registerSimpleItem("capacitor");
    /** Connectors: gold contacts in a plastic body, for cables and boards. */
    public static final DeferredItem<Item> CONNECTOR = Data_center_addon.ITEMS.registerSimpleItem("connector");
    /** Microchips, made on the chip production line from wafers, gold contacts and redstone. */
    public static final DeferredItem<Item> MICROCHIP = Data_center_addon.ITEMS.registerSimpleItem("microchip");

    // ------------------------------------------------------------------ chip production line

    public static final DeferredBlock<ChipFabControllerBlock> CHIP_FAB_CONTROLLER = Data_center_addon.BLOCKS.registerBlock(
            "chip_fab_controller", ChipFabControllerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(FactoryMachineBlock::lightLevel)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> CHIP_FAB_CONTROLLER_ITEM = Data_center_addon.ITEMS.registerSimpleBlockItem(CHIP_FAB_CONTROLLER);
    /** A cleanroom wall panel: part of the chip production line. */
    public static final DeferredBlock<Block> CLEANROOM_WALL = Data_center_addon.BLOCKS.registerSimpleBlock("cleanroom_wall",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> CLEANROOM_WALL_ITEM = Data_center_addon.ITEMS.registerSimpleBlockItem(CLEANROOM_WALL);
    /** A lithography unit: every one makes the chip production line one step faster. */
    public static final DeferredBlock<Block> LITHOGRAPHY_UNIT = Data_center_addon.BLOCKS.registerSimpleBlock("lithography_unit",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(s -> 6));
    public static final DeferredItem<BlockItem> LITHOGRAPHY_UNIT_ITEM = Data_center_addon.ITEMS.registerSimpleBlockItem(LITHOGRAPHY_UNIT);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChipFabBlockEntity>> CHIP_FAB_ENTITY =
            BLOCK_ENTITIES.register("chip_fab_controller",
                    () -> BlockEntityType.Builder.of(ChipFabBlockEntity::new, CHIP_FAB_CONTROLLER.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<ChipFabMenu>> CHIP_FAB_MENU = MENUS.register("chip_fab",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new ChipFabMenu(id, inventory, buf.readBlockPos())));

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

    // ------------------------------------------------------------------ hardware fab

    public static final DeferredBlock<HardwareFabBlock> HARDWARE_FAB = Data_center_addon.BLOCKS.registerBlock(
            "hardware_fab", HardwareFabBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(FactoryMachineBlock::lightLevel)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> HARDWARE_FAB_ITEM = Data_center_addon.ITEMS.registerSimpleBlockItem(HARDWARE_FAB);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HardwareFabBlockEntity>> HARDWARE_FAB_ENTITY =
            BLOCK_ENTITIES.register("hardware_fab",
                    () -> BlockEntityType.Builder.of(HardwareFabBlockEntity::new, HARDWARE_FAB.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<HardwareFabMenu>> HARDWARE_FAB_MENU = MENUS.register(
            "hardware_fab", () -> IMenuTypeExtension.create(HardwareFabMenu::new));

    // ------------------------------------------------------------------ factory manager

    public static final DeferredBlock<FactoryManagerBlock> FACTORY_MANAGER = Data_center_addon.BLOCKS.registerBlock(
            "factory_manager", FactoryManagerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion());
    public static final DeferredItem<BlockItem> FACTORY_MANAGER_ITEM = Data_center_addon.ITEMS.registerSimpleBlockItem(FACTORY_MANAGER);

    public static final DeferredHolder<MenuType<?>, MenuType<FactoryManagerMenu>> FACTORY_MANAGER_MENU = MENUS.register(
            "factory_manager",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new FactoryManagerMenu(id, inventory, buf.readBlockPos())));

    // ------------------------------------------------------------------ sales terminal

    public static final DeferredBlock<SalesTerminalBlock> SALES_TERMINAL = Data_center_addon.BLOCKS.registerBlock(
            "sales_terminal", SalesTerminalBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion());
    public static final DeferredItem<BlockItem> SALES_TERMINAL_ITEM = Data_center_addon.ITEMS.registerSimpleBlockItem(SALES_TERMINAL);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SalesTerminalBlockEntity>> SALES_TERMINAL_ENTITY =
            BLOCK_ENTITIES.register("sales_terminal",
                    () -> BlockEntityType.Builder.of(SalesTerminalBlockEntity::new, SALES_TERMINAL.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<SalesTerminalMenu>> SALES_TERMINAL_MENU = MENUS.register(
            "sales_terminal",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new SalesTerminalMenu(id, inventory, buf.readBlockPos())));

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
        ShopCatalog.register(new ShopEntry(ShopCategory.INFRASTRUCTURE, () -> new ItemStack(FACTORY_MANAGER_ITEM.get()),
                2_500_00L, MiningEra.FROM_WORLD_START));
        ShopCatalog.register(new ShopEntry(ShopCategory.INFRASTRUCTURE, () -> new ItemStack(HARDWARE_FAB_ITEM.get()),
                25_000_00L, MiningEra.FROM_WORLD_START));
        ShopCatalog.register(new ShopEntry(ShopCategory.INFRASTRUCTURE, () -> new ItemStack(SALES_TERMINAL_ITEM.get()),
                5_000_00L, MiningEra.FROM_WORLD_START));
        ShopCatalog.register(new ShopEntry(ShopCategory.INFRASTRUCTURE, () -> new ItemStack(CHIP_FAB_CONTROLLER_ITEM.get()),
                40_000_00L, MiningEra.FROM_WORLD_START));
        ShopCatalog.register(new ShopEntry(ShopCategory.INFRASTRUCTURE, () -> new ItemStack(CLEANROOM_WALL_ITEM.get()),
                200_00L, MiningEra.FROM_WORLD_START));
        ShopCatalog.register(new ShopEntry(ShopCategory.INFRASTRUCTURE, () -> new ItemStack(LITHOGRAPHY_UNIT_ITEM.get()),
                15_000_00L, MiningEra.FROM_WORLD_START));
        for (MachineKind kind : MachineKind.values()) {
            DeferredItem<BlockItem> item = MACHINE_ITEMS.get(kind);
            ShopCatalog.register(new ShopEntry(ShopCategory.INFRASTRUCTURE, () -> new ItemStack(item.get()), kind.priceCents,
                    MiningEra.FROM_WORLD_START));
        }
    }

    /** For the creative tab. */
    public static List<ItemStack> creativeStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(FACTORY_MANAGER_ITEM.get().getDefaultInstance());
        stacks.add(PURCHASE_TERMINAL_ITEM.get().getDefaultInstance());
        stacks.add(SALES_TERMINAL_ITEM.get().getDefaultInstance());
        MACHINE_ITEMS.values().forEach(item -> stacks.add(item.get().getDefaultInstance()));
        stacks.add(CHIP_FAB_CONTROLLER_ITEM.get().getDefaultInstance());
        stacks.add(HARDWARE_FAB_ITEM.get().getDefaultInstance());
        stacks.add(CLEANROOM_WALL_ITEM.get().getDefaultInstance());
        stacks.add(LITHOGRAPHY_UNIT_ITEM.get().getDefaultInstance());
        stacks.add(PLASTIC_PELLETS.get().getDefaultInstance());
        stacks.add(SILICON.get().getDefaultInstance());
        stacks.add(COPPER_FOIL.get().getDefaultInstance());
        stacks.add(GOLD_CONTACTS.get().getDefaultInstance());
        stacks.add(COPPER_WIRE.get().getDefaultInstance());
        stacks.add(PLASTIC_PARTS.get().getDefaultInstance());
        stacks.add(PCB_BLANK.get().getDefaultInstance());
        stacks.add(WAFER.get().getDefaultInstance());
        stacks.add(CIRCUIT_BOARD.get().getDefaultInstance());
        stacks.add(CAPACITOR.get().getDefaultInstance());
        stacks.add(CONNECTOR.get().getDefaultInstance());
        stacks.add(MICROCHIP.get().getDefaultInstance());
        return List.copyOf(stacks);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // hoppers and pipes may take the goods, but put nothing in
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PURCHASE_TERMINAL_ENTITY.get(), (be, side) -> be.output());
        // the sales terminal takes products from any side and gives nothing back
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SALES_TERMINAL_ENTITY.get(), (be, side) -> be.input());
        // machines: inputs in, outputs out, power from every side
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MACHINE_ENTITY.get(), (be, side) -> be.automation());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MACHINE_ENTITY.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CHIP_FAB_ENTITY.get(), (be, side) -> be.automation());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, HARDWARE_FAB_ENTITY.get(), (be, side) -> be.automation());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, HARDWARE_FAB_ENTITY.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CHIP_FAB_ENTITY.get(), (be, side) -> be.energy());
    }
}
