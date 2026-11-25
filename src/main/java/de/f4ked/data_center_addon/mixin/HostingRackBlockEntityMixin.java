package de.f4ked.data_center_addon.mixin;

import com.example.datacenter.component.StorageSpec;
import com.example.datacenter.hosting.HostingDemand;
import com.example.datacenter.hosting.HostingMode;
import com.example.datacenter.hosting.HostingModeRates;
import com.example.datacenter.hosting.HostingProduct;
import com.example.datacenter.hosting.HostingRackBlockEntity;
import com.example.datacenter.hosting.HostingReputation;
import com.example.datacenter.machine.MachineBlockEntity;
import com.example.datacenter.wallet.OwnerMessages;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.hosting.market.MarketService;
import de.f4ked.data_center_addon.hosting.market.ServiceQuality;
import de.f4ked.data_center_addon.hosting.reputation.LossNotices;
import de.f4ked.data_center_addon.hosting.reputation.RackService;
import de.f4ked.data_center_addon.hosting.reputation.ReputationService;
import de.f4ked.data_center_addon.hosting.reputation.Sensitivity;
import de.f4ked.data_center_addon.hosting.startup.StartupService;
import de.f4ked.data_center_addon.part.HostingStorageBoost;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Hooks into the Hosting Rack's {@code runLoadout}, which runs once per tick while the rack works:
 * <ul>
 *   <li>It reads the capacity of every storage part of its servers: once for the storage rent
 *       (dedicated mode) and once for the shared capacity pool. Both reads go through
 *       {@link HostingStorageBoost}, which raises the capacity of the add-on's storage parts.</li>
 *   <li>At its end the rack's income of the tick is reported to {@link StartupService} (start bonus,
 *       service tickets). This hook only reads.</li>
 *   <li>Shared, VPS and colocation customers pay the owner's price ({@code MarketService#priceFactor}).</li>
 *   <li>With the market model on, Data Center's market mood no longer scales shared and VPS income; it
 *       changes the markets' size instead.</li>
 *   <li>The rack's service of the tick (customers served, down, overloaded, throttled) goes to the
 *       add-on's {@link RackService} instead of straight to Data Center's reputation, and to
 *       {@link ServiceQuality}.</li>
 * </ul>
 * {@code require = 0}: if a Data Center update changes this method, the hooks silently stop instead
 * of crashing the game.
 */
@Mixin(value = HostingRackBlockEntity.class, remap = false)
public abstract class HostingRackBlockEntityMixin {
    /** What this rack's customers pay relative to the list price this tick (the owner's price, Market tab). */
    @Unique
    private double data_center_addon$price = 1.0;

    @Inject(method = "runLoadout", at = @At("HEAD"), require = 0)
    private void data_center_addon$readPrice(MachineBlockEntity.TickContext context, CallbackInfo ci) {
        HostingRackBlockEntity rack = (HostingRackBlockEntity) (Object) this;
        data_center_addon$price = rack.getLevel() instanceof ServerLevel level
                ? MarketService.priceFactor(level, OwnerMessages.ownerOf(level, rack.getBlockPos()), rack.getProduct())
                : 1.0;
    }

    /** Shared hosting and VPS: what a customer brings, at the owner's price. */
    @Redirect(
            method = "runLoadout",
            at = @At(value = "INVOKE",
                    target = "Lcom/example/datacenter/hosting/HostingModeRates;unitNet(Lcom/example/datacenter/hosting/HostingProduct;Ljava/time/LocalDate;)D"),
            require = 0)
    private double data_center_addon$unitNet(HostingProduct product, LocalDate today) {
        return HostingModeRates.unitNet(product, today) * data_center_addon$price;
    }

    /** Colocation: what a customer's server brings, at the owner's price. */
    @Redirect(
            method = "runLoadout",
            at = @At(value = "INVOKE",
                    target = "Lcom/example/datacenter/hosting/HostingModeRates;coloUsdPerServer(Ljava/time/LocalDate;)D"),
            require = 0)
    private double data_center_addon$coloUsd(LocalDate today) {
        return HostingModeRates.coloUsdPerServer(today) * data_center_addon$price;
    }

    @Redirect(
            method = "runLoadout",
            at = @At(value = "INVOKE", target = "Lcom/example/datacenter/component/StorageSpec;getCapacityGb()J"),
            require = 0)
    private long data_center_addon$boostHostedStorage(StorageSpec spec) {
        return HostingStorageBoost.capacityGb(spec);
    }

    /**
     * Data Center's market mood (dotcom crash ...) multiplies the income of shared hosting and VPS racks.
     * With the market model on, the mood changes the size of the markets instead ({@code MarketEvents}),
     * so it no longer touches these racks' income. Dedicated servers keep it; colocation never had it.
     */
    @Redirect(
            method = "runLoadout",
            at = @At(value = "INVOKE",
                    target = "Lcom/example/datacenter/hosting/HostingDemand;factor(Lnet/minecraft/world/level/Level;Ljava/time/LocalDate;Lcom/example/datacenter/hosting/HostingMode;)D"),
            require = 0)
    private double data_center_addon$marketMood(Level level, LocalDate today, HostingMode mode) {
        if (Config.marketModel() && (mode == HostingMode.SHARED || mode == HostingMode.VPS)) {
            return 1.0;
        }
        return HostingDemand.factor(level, today, mode);
    }

    /**
     * Passes the rack's service (shared, VPS, colocation) on to the reputation ({@link RackService}) and to
     * {@link ServiceQuality}.
     */
    @Redirect(
            method = "runLoadout",
            at = @At(value = "INVOKE",
                    target = "Lcom/example/datacenter/hosting/HostingReputation;recordService(Lnet/minecraft/server/MinecraftServer;DZIDD)V"),
            require = 0)
    private void data_center_addon$recordService(HostingReputation reputation, MinecraftServer server, double earning,
                                                 boolean throttled, int certification, double down, double overloaded) {
        HostingRackBlockEntity rack = (HostingRackBlockEntity) (Object) this;
        UUID owner = rack.getLevel() instanceof ServerLevel level ? OwnerMessages.ownerOf(level, rack.getBlockPos()) : null;
        // complaints by segment: game server customers mind an outage four times as much as company websites
        double weightedDown = Config.reputationModel() && rack.getLevel() instanceof ServerLevel level
                ? down * Sensitivity.factor(level, owner, rack.getProduct()) : down;
        RackService.service(rack, reputation, server, owner, earning, throttled, certification, weightedDown, overloaded);
        ServiceQuality.report(owner, rack.getProduct(), earning, throttled, weightedDown, overloaded);
    }

    /** Dedicated servers: their service goes to the reputation ({@link RackService}). */
    @Redirect(
            method = "runLoadout",
            at = @At(value = "INVOKE",
                    target = "Lcom/example/datacenter/hosting/HostingReputation;recordService(Lnet/minecraft/server/MinecraftServer;IZII)V"),
            require = 0)
    private void data_center_addon$recordDedicated(HostingReputation reputation, MinecraftServer server, int earningBays,
                                                   boolean throttled, int certification, int downBays) {
        HostingRackBlockEntity rack = (HostingRackBlockEntity) (Object) this;
        UUID owner = rack.getLevel() instanceof ServerLevel level ? OwnerMessages.ownerOf(level, rack.getBlockPos()) : null;
        RackService.service(rack, reputation, server, owner, earningBays, throttled, certification, downBays, 0);
    }

    /**
     * A crash. Data Center takes 20 points at once; with the reputation model on the crash is a serious
     * incident that presses the account's Ruf for 30 days instead.
     */
    @Redirect(
            method = "runLoadout",
            at = @At(value = "INVOKE",
                    target = "Lcom/example/datacenter/hosting/HostingReputation;recordCrash(Lnet/minecraft/server/MinecraftServer;)V"),
            require = 0)
    private void data_center_addon$recordCrash(HostingReputation reputation, MinecraftServer server) {
        HostingRackBlockEntity rack = (HostingRackBlockEntity) (Object) this;
        if (!Config.reputationModel()) {
            reputation.recordCrash(server);
        } else if (rack.getLevel() instanceof ServerLevel level) {
            UUID owner = OwnerMessages.ownerOf(level, rack.getBlockPos());
            ReputationService.crash(server, owner);
            LossNotices.crash(level, rack.getBlockPos(), owner);
        }
    }

    /** Reports the rack and its income of this tick to the founding phase (start bonus, service tickets). */
    @Inject(method = "runLoadout", at = @At("TAIL"), require = 0)
    private void data_center_addon$reportIncome(MachineBlockEntity.TickContext context, CallbackInfo ci) {
        StartupService.onRackTick((HostingRackBlockEntity) (Object) this);
    }
}
