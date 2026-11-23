package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.component.StorageSpec;
import com.example.datacenter.hosting.HostingRackBlockEntity;
import de.f4ked.data_center_addon.part.HostingStorageBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The Hosting Rack reads the capacity of every storage part of its servers in {@code runLoadout}:
 * once for the storage rent (dedicated mode) and once for the shared capacity pool. Both reads go
 * through {@link HostingStorageBoost}, which raises the capacity of the add-on's storage parts.
 * <p>
 * {@code require = 0}: if a Data Center update changes this method, the boost silently stops
 * instead of crashing the game.
 */
@Mixin(value = HostingRackBlockEntity.class, remap = false)
public abstract class HostingRackBlockEntityMixin {
    @Redirect(
            method = "runLoadout",
            at = @At(value = "INVOKE", target = "Lcom/example/datacenter/component/StorageSpec;getCapacityGb()J"),
            require = 0)
    private long data_center_addon$boostHostedStorage(StorageSpec spec) {
        return HostingStorageBoost.capacityGb(spec);
    }
}
