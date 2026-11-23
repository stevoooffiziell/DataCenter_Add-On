package de.f4ked.data_center_addon.part;

import com.example.datacenter.catalog.PartDef;
import com.example.datacenter.catalog.PartPacks;
import com.example.datacenter.component.StorageSpec;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.Data_center_addon;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Capacity boost for this add-on's storage parts inside a Hosting Rack (called from
 * {@code HostingRackBlockEntityMixin}).
 * <p>
 * Data Center hands out one spec object per data-pack part ({@link PartPacks#packSpec}), so the
 * add-on's storage parts are recognised by identity. The set is rebuilt whenever the part packs are
 * re-applied ({@link PartPacks#generation()} changes).
 */
public final class HostingStorageBoost {
    private static long cachedGeneration = Long.MIN_VALUE;
    private static Set<Object> addonStorage = Set.of();

    private HostingStorageBoost() {
    }

    public static long capacityGb(StorageSpec spec) {
        long capacity = spec.getCapacityGb();
        double boost = Config.hostingStorageBoost();
        if (boost <= 1.0 || !isAddonPart(spec)) {
            return capacity;
        }
        return Math.round(capacity * boost);
    }

    private static synchronized boolean isAddonPart(StorageSpec spec) {
        long generation = PartPacks.generation();
        if (generation != cachedGeneration) {
            addonStorage = collectAddonStorage();
            cachedGeneration = generation;
        }
        return addonStorage.contains(spec);
    }

    private static Set<Object> collectAddonStorage() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return Set.of();
        }
        Registry<PartDef> parts = server.registryAccess().registry(PartPacks.KEY).orElse(null);
        if (parts == null) {
            return Set.of();
        }
        Set<Object> specs = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ResourceLocation id : parts.keySet()) {
            if (Data_center_addon.MODID.equals(id.getNamespace()) && PartPacks.packSpec(id) instanceof StorageSpec storage) {
                specs.add(storage);
            }
        }
        return specs;
    }
}
