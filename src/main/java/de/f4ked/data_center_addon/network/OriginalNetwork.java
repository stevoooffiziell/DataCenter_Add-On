package de.f4ked.data_center_addon.network;

import com.example.datacenter.shop.ShopCatalog;
import com.example.datacenter.shop.ShopEntry;
import de.f4ked.data_center_addon.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Data Center's own cables and switches, hidden when {@code hideOriginalNetwork} is on: its switch
 * leaves the Shop (here), its cable recipes are switched off by data-pack conditions. Cables and
 * switches already placed keep working.
 */
public final class OriginalNetwork {
    private static final Set<ResourceLocation> SHOP_ITEMS = Set.of(
            ResourceLocation.fromNamespaceAndPath("datacenter", "switch"));

    private static Set<ShopEntry> entries;

    private OriginalNetwork() {
    }

    public static boolean hidden(ShopEntry entry) {
        return Config.hidesOriginalNetwork() && entries().contains(entry);
    }

    private static synchronized Set<ShopEntry> entries() {
        if (entries == null) {
            Set<ShopEntry> found = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ShopEntry entry : ShopCatalog.staticEntries()) {
                if (entry.partId() == null && SHOP_ITEMS.contains(BuiltInRegistries.ITEM.getKey(entry.createStack().getItem()))) {
                    found.add(entry);
                }
            }
            entries = found;
        }
        return entries;
    }
}
