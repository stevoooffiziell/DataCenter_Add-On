package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.hosting.HostingCustomers;
import com.example.datacenter.hosting.HostingProduct;
import de.f4ked.data_center_addon.hosting.market.MarketModel;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Data Center's customer book moves every product of every account on by one tick in {@code step}. The
 * add-on's market model ({@link MarketModel}) watches each step.
 * <p>
 * {@code require = 0}: if a Data Center update changes this method, the hook silently stops instead of
 * crashing the game.
 */
@Mixin(value = HostingCustomers.class, remap = false)
public abstract class HostingCustomersMixin {
    @Inject(method = "step", at = @At("HEAD"), require = 0)
    private void data_center_addon$step(@Nullable UUID owner, HostingProduct product, LocalDate date, double reputationPoints,
                                        double places, double dtMonths, CallbackInfo ci) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            MarketModel.observe(server, owner, product, ((HostingCustomers) (Object) this).total(owner, product), places, dtMonths);
        }
    }
}
