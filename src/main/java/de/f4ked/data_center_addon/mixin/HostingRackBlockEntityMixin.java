package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.component.StorageSpec;
import com.example.datacenter.hosting.HostingRackBlockEntity;
import com.example.datacenter.machine.MachineBlockEntity;
import de.f4ked.data_center_addon.hosting.startup.StartupService;
import de.f4ked.data_center_addon.part.HostingStorageBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two hooks into the Hosting Rack's {@code runLoadout}, which runs once per tick while the rack works:
 * <ul>
 *   <li>It reads the capacity of every storage part of its servers: once for the storage rent
 *       (dedicated mode) and once for the shared capacity pool. Both reads go through
 *       {@link HostingStorageBoost}, which raises the capacity of the add-on's storage parts.</li>
 *   <li>At its end the rack's income of the tick is reported to {@link StartupService} (start bonus,
 *       service tickets). This hook only reads.</li>
 * </ul>
 * {@code require = 0}: if a Data Center update changes this method, the hooks silently stop instead
 * of crashing the game.
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

    /** Reports the rack and its income of this tick to the founding phase (start bonus, service tickets). */
    @Inject(method = "runLoadout", at = @At("TAIL"), require = 0)
    private void data_center_addon$reportIncome(MachineBlockEntity.TickContext context, CallbackInfo ci) {
        StartupService.onRackTick((HostingRackBlockEntity) (Object) this);
    }
}
