package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.hosting.CustomerClass;
import com.example.datacenter.hosting.HostingReputation;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.hosting.reputation.ClassRules;
import de.f4ked.data_center_addon.hosting.reputation.ReputationService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * Data Center decides an account's customer class from its reputation points alone (150, 350, 600, 850)
 * every time the points change ({@code adjust} calls {@code classFor}). With the add-on's reputation
 * model on, the class comes from the Ruf and the renown together ({@link ClassRules}). Data Center's
 * messages and advancements for a new class stay.
 * <p>
 * {@code require = 0}: if a Data Center update changes this method, Data Center's own classes apply.
 */
@Mixin(value = HostingReputation.class, remap = false)
public abstract class HostingReputationMixin {
    @Shadow
    @Final
    @Nullable
    private UUID owner;

    @Redirect(
            method = "adjust",
            at = @At(value = "INVOKE",
                    target = "Lcom/example/datacenter/hosting/HostingReputation;classFor(DLcom/example/datacenter/hosting/CustomerClass;)Lcom/example/datacenter/hosting/CustomerClass;"),
            require = 0)
    private CustomerClass data_center_addon$classFor(double points, CustomerClass current) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (!Config.reputationModel() || server == null) {
            return dataCenterClass(points, current);
        }
        return ClassRules.classFor(ReputationService.ruf(server, owner), points, current);
    }

    /** Data Center's own rule: the best class by points, kept within 25 points below its threshold. */
    private static CustomerClass dataCenterClass(double points, CustomerClass current) {
        CustomerClass best = CustomerClass.HOBBYISTS;
        for (CustomerClass c : CustomerClass.values()) {
            if (points >= c.threshold) {
                best = c;
            }
        }
        return best.ordinal() < current.ordinal() && points >= current.threshold - 25 ? current : best;
    }
}
