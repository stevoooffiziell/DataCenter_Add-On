package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.hosting.CustomerClass;
import com.example.datacenter.hosting.HostingReputation;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.hosting.reputation.ClassRules;
import de.f4ked.data_center_addon.hosting.reputation.ReputationService;
import com.example.datacenter.wallet.OwnerMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * Data Center decides an account's customer class from its reputation points alone (150, 350, 600, 850)
 * every time the points change ({@code adjust} calls {@code classFor}). With the add-on's reputation
 * model on, the class comes from the Ruf and the renown together, on the add-on's finer ladder of
 * classes ({@link ClassRules}, {@link de.f4ked.data_center_addon.hosting.reputation.CustomerTier}); Data
 * Center keeps the class of its own that the add-on's class belongs to. Data Center's advancements for a
 * new class stay; its message is replaced by the add-on's, which names the finer class. The renown goes
 * up to {@link ClassRules#MAX_RENOWN} instead of 1000.
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
        return ClassRules.update(server, owner, ReputationService.ruf(server, owner), points, current);
    }

    /** Data Center keeps the points within 0..1000; with the reputation model on, up to 2500. */
    @ModifyConstant(method = "adjust", constant = @Constant(doubleValue = 1000.0), require = 0)
    private double data_center_addon$maxPoints(double max) {
        return ClassRules.maxRenown();
    }

    /** Data Center's message for a new class; with the reputation model on {@link ClassRules} sends its own. */
    @Redirect(
            method = "adjust",
            at = @At(value = "INVOKE",
                    target = "Lcom/example/datacenter/wallet/OwnerMessages;toOwner(Lnet/minecraft/server/MinecraftServer;Ljava/util/UUID;Lnet/minecraft/network/chat/Component;)V"),
            require = 0)
    private void data_center_addon$classMessage(MinecraftServer server, UUID owner, Component message) {
        if (!Config.reputationModel()) {
            OwnerMessages.toOwner(server, owner, message);
        }
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
