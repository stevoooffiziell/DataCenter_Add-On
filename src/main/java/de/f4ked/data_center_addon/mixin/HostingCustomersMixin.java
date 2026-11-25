package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.hosting.CustomerClass;
import com.example.datacenter.hosting.HostingCustomers;
import com.example.datacenter.hosting.HostingProduct;
import com.example.datacenter.hosting.HostingReputation;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.hosting.market.Competitors;
import de.f4ked.data_center_addon.hosting.market.MarketBook;
import de.f4ked.data_center_addon.hosting.market.MarketEvents;
import de.f4ked.data_center_addon.hosting.market.MarketModel;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Data Center's customer book moves every product of every account on by one tick in {@code step}. With
 * the market model on ({@link Config#marketModel()}) the add-on's {@link MarketModel} takes that step
 * instead; otherwise it only watches.
 * <p>
 * {@code require = 0}: if a Data Center update changes this method, the hook silently stops and Data
 * Center's own model runs, instead of crashing the game.
 */
@Mixin(value = HostingCustomers.class, remap = false)
public abstract class HostingCustomersMixin {
    @Shadow
    @Final
    private Map<UUID, EnumMap<HostingProduct, HostingCustomers.Flow>> flows;

    @Shadow
    private double[] countsOf(@Nullable UUID owner, HostingProduct product) {
        throw new AssertionError();
    }

    @Shadow
    private static UUID key(@Nullable UUID owner) {
        throw new AssertionError();
    }

    @Inject(method = "step", at = @At("HEAD"), cancellable = true, require = 0)
    private void data_center_addon$step(@Nullable UUID owner, HostingProduct product, LocalDate date, double reputationPoints,
                                        double places, double dtMonths, CallbackInfo ci) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        HostingCustomers self = (HostingCustomers) (Object) this;
        MarketBook.Entry entry = MarketModel.observe(server, owner, product, self.total(owner, product), places, dtMonths);
        if (!Config.marketModel()) {
            return;
        }
        MarketBook book = MarketBook.get(server);
        CustomerClass unlocked = HostingReputation.get(server.overworld(), owner).getUnlockedClass();
        double market = MarketEvents.factor(server.overworld(), product, date);
        double rivals = Competitors.factor(book, product);
        MarketModel.Reach reach = MarketModel.sharedReach(book, entry, product, date, reputationPoints, unlocked, market, rivals,
                server.getTickCount(), true);
        MarketModel.Flow flow = MarketModel.step(entry, product, countsOf(owner, product), date, reach, places, dtMonths);
        flows.computeIfAbsent(key(owner), k -> new EnumMap<>(HostingProduct.class))
                .put(product, new HostingCustomers.Flow(flow.orders(), flow.cancellations()));
        self.setDirty();
        ci.cancel();
    }
}
