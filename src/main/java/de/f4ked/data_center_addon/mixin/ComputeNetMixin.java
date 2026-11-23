package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.net.ComputeNet;
import com.example.datacenter.net.NetLimits;
import com.example.datacenter.net.NetNode;
import com.example.datacenter.net.NetStatus;
import de.f4ked.data_center_addon.network.NetworkTopology;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Applies the add-on's cable and switch rules ({@link NetworkTopology}) to a network's capacity.
 * Data Center computes {@code cap} as the capacity of the weakest cable grade in the network; every
 * value stored into it is limited to the add-on's capacity, so the smaller of both wins.
 * <p>
 * {@code require = 0}: if a Data Center update changes this method, the add-on's cables keep
 * connecting but stop limiting the network, instead of crashing the game.
 */
@Mixin(value = ComputeNet.class, remap = false)
public abstract class ComputeNetMixin {
    @Shadow
    @Final
    Set<NetNode> members;

    @Unique
    private long data_center_addon$addonCapacity = Long.MAX_VALUE;

    @Inject(method = "compute", at = @At("HEAD"), require = 0)
    private void data_center_addon$computeAddonCapacity(NetLimits limits, CallbackInfoReturnable<NetStatus> cir) {
        data_center_addon$addonCapacity = NetworkTopology.capacity((ComputeNet) (Object) this, members);
    }

    @ModifyVariable(method = "compute", at = @At("STORE"), name = "cap", require = 0)
    private long data_center_addon$limitCapacity(long cap) {
        return Math.min(cap, data_center_addon$addonCapacity);
    }
}
