package de.f4ked.data_center_addon.hosting;

import com.example.datacenter.mining.MiningEra;
import com.example.datacenter.shop.ShopCatalog;
import com.example.datacenter.shop.ShopCategory;
import com.example.datacenter.shop.ShopEntry;
import de.f4ked.data_center_addon.Data_center_addon;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block, item, menu and Shop entry of the Hosting Manager. */
public final class HostingContent {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Data_center_addon.MODID);

    public static final DeferredBlock<HostingManagerBlock> MANAGER = Data_center_addon.BLOCKS.registerBlock(
            "hosting_manager", HostingManagerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion());
    public static final DeferredItem<BlockItem> MANAGER_ITEM = Data_center_addon.ITEMS.registerSimpleBlockItem(MANAGER);

    public static final DeferredHolder<MenuType<?>, MenuType<HostingManagerMenu>> MENU = MENUS.register("hosting_manager",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new HostingManagerMenu(id, inventory, buf.readBlockPos())));

    /** Shop price of the Hosting Manager in US cents. */
    private static final long PRICE_CENTS = 2_500_00L;

    private HostingContent() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
        ShopCatalog.register(new ShopEntry(ShopCategory.INFRASTRUCTURE, () -> new ItemStack(MANAGER_ITEM.get()),
                PRICE_CENTS, MiningEra.FROM_WORLD_START));
    }
}
