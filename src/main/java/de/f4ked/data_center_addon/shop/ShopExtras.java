package de.f4ked.data_center_addon.shop;

import com.example.datacenter.mining.MiningEra;
import com.example.datacenter.registry.ModBlocks;
import com.example.datacenter.shop.ShopCatalog;
import com.example.datacenter.shop.ShopCategory;
import com.example.datacenter.shop.ShopEntry;
import de.f4ked.data_center_addon.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Shop entries beyond Data Center's own range:
 * <ul>
 *   <li>The bigger Mekanism energy cubes and the parts of the induction matrix (Data Center only sells
 *       the basic energy cube). They show only with Mekanism installed.</li>
 *   <li>Every Mekanism transport tier, which Data Center only shows with its arcade catalog
 *       ({@link Config#mekanismTiers()}, {@code ShopCatalogMixin}).</li>
 *   <li>The AC Indoor and Outdoor Unit, which Data Center retired from the shop and which have no recipe
 *       ({@link Config#acUnits()}).</li>
 * </ul>
 */
public final class ShopExtras {
    /** Mekanism items and their prices in US cents (Data Center sells the basic energy cube for $20). */
    private static final String[] MEKANISM_ITEMS = {
            "advanced_energy_cube", "elite_energy_cube", "ultimate_energy_cube",
            "induction_casing", "induction_port",
            "basic_induction_cell", "basic_induction_provider",
            "advanced_induction_cell", "advanced_induction_provider"};
    private static final long[] MEKANISM_PRICES = {
            20_000L, 80_000L, 320_000L,
            5_000L, 25_000L,
            150_000L, 100_000L,
            600_000L, 400_000L};

    private static final Map<ShopEntry, Boolean> AC_CACHE = new IdentityHashMap<>();

    private ShopExtras() {
    }

    /** Registers the extra entries; called while the add-on is constructed, like its other shop entries. */
    public static void register() {
        for (int i = 0; i < MEKANISM_ITEMS.length; i++) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mekanism", MEKANISM_ITEMS[i]);
            ShopCatalog.register(new ShopEntry(ShopCategory.MEKANISM, () -> {
                Item item = BuiltInRegistries.ITEM.get(id);
                return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
            }, MEKANISM_PRICES[i], MiningEra.FROM_WORLD_START));
        }
    }

    /** Whether an arcade-only entry is sold anyway: the Mekanism transports, with the config on. */
    public static boolean unlocksArcade(ShopEntry entry) {
        return entry.category() == ShopCategory.MEKANISM && Config.mekanismTiers();
    }

    /** Whether a retired entry is sold anyway: the split air conditioning units, with the config on. */
    public static boolean unretires(ShopEntry entry) {
        if (entry.category() != ShopCategory.INFRASTRUCTURE || !Config.acUnits()) {
            return false;
        }
        synchronized (AC_CACHE) {
            return AC_CACHE.computeIfAbsent(entry, e -> {
                Item item = e.createStack().getItem();
                return item == ModBlocks.AC_INDOOR_UNIT.get().asItem() || item == ModBlocks.AC_OUTDOOR_UNIT.get().asItem();
            });
        }
    }
}
