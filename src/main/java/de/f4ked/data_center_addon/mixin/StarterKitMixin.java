package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.StarterKit;
import de.f4ked.data_center_addon.hosting.HostingContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Data Center's starter kit (on first join, on a new island or by command) also holds the add-on's
 * Hosting Manager. {@code grant} returns true only when it handed the kit out, so a player gets the
 * manager once, with the kit.
 * <p>
 * {@code require = 0}: if a Data Center update changes this method, the kit comes without the manager.
 */
@Mixin(value = StarterKit.class, remap = false)
public abstract class StarterKitMixin {
    @Inject(method = "grant", at = @At("RETURN"), require = 0)
    private static void data_center_addon$addManager(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        ItemStack manager = new ItemStack(HostingContent.MANAGER_ITEM.get());
        if (!player.getInventory().add(manager)) {
            player.drop(manager, false);
        }
    }
}
