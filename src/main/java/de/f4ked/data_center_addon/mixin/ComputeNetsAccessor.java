package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.net.ComputeGrid;
import com.example.datacenter.net.ComputeNets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Finds the level a compute grid belongs to. */
@Mixin(value = ComputeNets.class, remap = false)
public interface ComputeNetsAccessor {
    @Accessor("BY_LEVEL")
    static Map<ResourceKey<Level>, ComputeNets> data_center_addon$byLevel() {
        throw new AssertionError();
    }

    @Accessor("grid")
    ComputeGrid data_center_addon$grid();
}
