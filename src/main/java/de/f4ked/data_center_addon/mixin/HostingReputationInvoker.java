package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.hosting.HostingReputation;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Data Center's private {@code adjust}: with a change of 0 it re-checks the customer class (the Ruf can
 * change without the points changing).
 */
@Mixin(value = HostingReputation.class, remap = false)
public interface HostingReputationInvoker {
    @Invoker("adjust")
    void data_center_addon$adjust(MinecraftServer server, double delta);
}
