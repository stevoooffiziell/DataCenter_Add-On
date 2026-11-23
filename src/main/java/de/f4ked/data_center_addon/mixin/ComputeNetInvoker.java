package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.net.ComputeNet;
import com.example.datacenter.net.NetNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

/** Package-private parts of Data Center's compute networks. */
@Mixin(value = ComputeNet.class, remap = false)
public interface ComputeNetInvoker {
    /** Drops the cached status, e.g. when a switch loses power. */
    @Invoker("invalidate")
    void data_center_addon$invalidate();

    @Accessor("members")
    Set<NetNode> data_center_addon$members();
}
