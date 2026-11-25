package de.f4ked.data_center_addon.hosting;

import com.example.datacenter.api.DataCenterApi;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.client.hosting.HostingManagerClient;
import de.f4ked.data_center_addon.hosting.contract.ContractService;
import de.f4ked.data_center_addon.hosting.contract.DeliveryService;
import de.f4ked.data_center_addon.hosting.market.MarketService;
import de.f4ked.data_center_addon.hosting.reputation.Campaigns;
import de.f4ked.data_center_addon.hosting.startup.StartupService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Network traffic of the Hosting Manager: the server sends the figures to whoever has a manager open
 * (on opening, after every change and once a second), the client sends the changes made on screen.
 */
@EventBusSubscriber(modid = Data_center_addon.MODID)
public final class HostingManagerPayloads {
    private static final int INTERVAL_TICKS = 20;
    private static final String NETWORK_VERSION = ModList.get().getModContainerById(Data_center_addon.MODID)
            .map(c -> c.getModInfo().getVersion().toString()).orElse("0");

    private HostingManagerPayloads() {
    }

    public record Snapshot(BlockPos manager, HostingSnapshot snapshot) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "hosting_snapshot"));
        public static final StreamCodec<FriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Snapshot::manager,
                HostingSnapshot.STREAM_CODEC, Snapshot::snapshot,
                Snapshot::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Change(BlockPos manager, BlockPos rack, HostingManagerService.Action action) implements CustomPacketPayload {
        public static final Type<Change> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "hosting_change"));
        public static final StreamCodec<FriendlyByteBuf, Change> STREAM_CODEC = StreamCodec.of(
                (buf, c) -> {
                    buf.writeBlockPos(c.manager);
                    buf.writeBlockPos(c.rack);
                    buf.writeEnum(c.action);
                },
                buf -> new Change(buf.readBlockPos(), buf.readBlockPos(), buf.readEnum(HostingManagerService.Action.class)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Accept an offer or cancel a running contract, from the manager's Contracts tab. */
    public record ContractChange(BlockPos manager, int contract, ContractService.Action action) implements CustomPacketPayload {
        public static final Type<ContractChange> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "hosting_contract"));
        public static final StreamCodec<FriendlyByteBuf, ContractChange> STREAM_CODEC = StreamCodec.of(
                (buf, c) -> {
                    buf.writeBlockPos(c.manager);
                    buf.writeVarInt(c.contract);
                    buf.writeEnum(c.action);
                },
                buf -> new ContractChange(buf.readBlockPos(), buf.readVarInt(), buf.readEnum(ContractService.Action.class)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Accept, decline, deliver to or cancel a delivery order, from the manager's Contracts tab. */
    public record DeliveryChange(BlockPos manager, int delivery, DeliveryService.Action action) implements CustomPacketPayload {
        public static final Type<DeliveryChange> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "hosting_delivery"));
        public static final StreamCodec<FriendlyByteBuf, DeliveryChange> STREAM_CODEC = StreamCodec.of(
                (buf, c) -> {
                    buf.writeBlockPos(c.manager);
                    buf.writeVarInt(c.delivery);
                    buf.writeEnum(c.action);
                },
                buf -> new DeliveryChange(buf.readBlockPos(), buf.readVarInt(), buf.readEnum(DeliveryService.Action.class)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Take or repay the founding loan, from the manager's Startup tab. */
    public record StartupChange(BlockPos manager, StartupService.Action action) implements CustomPacketPayload {
        public static final Type<StartupChange> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "hosting_startup"));
        public static final StreamCodec<FriendlyByteBuf, StartupChange> STREAM_CODEC = StreamCodec.of(
                (buf, c) -> {
                    buf.writeBlockPos(c.manager);
                    buf.writeEnum(c.action);
                },
                buf -> new StartupChange(buf.readBlockPos(), buf.readEnum(StartupService.Action.class)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Raise or lower the price of a product by one step, from the manager's Market tab. */
    public record PriceChange(BlockPos manager, int product, int delta) implements CustomPacketPayload {
        public static final Type<PriceChange> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "hosting_price"));
        public static final StreamCodec<FriendlyByteBuf, PriceChange> STREAM_CODEC = StreamCodec.of(
                (buf, c) -> {
                    buf.writeBlockPos(c.manager);
                    buf.writeVarInt(c.product);
                    buf.writeVarInt(c.delta);
                },
                buf -> new PriceChange(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Book an advertising campaign, from the manager's Reputation tab. */
    public record CampaignBooking(BlockPos manager, int campaign) implements CustomPacketPayload {
        public static final Type<CampaignBooking> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "hosting_campaign"));
        public static final StreamCodec<FriendlyByteBuf, CampaignBooking> STREAM_CODEC = StreamCodec.of(
                (buf, c) -> {
                    buf.writeBlockPos(c.manager);
                    buf.writeVarInt(c.campaign);
                },
                buf -> new CampaignBooking(buf.readBlockPos(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // the add-on's version: the snapshot changes with most releases, so server and client must match
        // exactly (a mismatch is refused at login)
        var registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(Snapshot.TYPE, Snapshot.STREAM_CODEC,
                (payload, context) -> HostingManagerClient.receive(payload.manager(), payload.snapshot()));
        registrar.playToServer(Change.TYPE, Change.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                HostingManagerService.apply(player, payload.manager(), payload.rack(), payload.action());
                if (player.level() instanceof ServerLevel level) {
                    sendSnapshot(player, level, payload.manager());
                }
            }
        });
        registrar.playToServer(ContractChange.TYPE, ContractChange.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level
                    && player.containerMenu instanceof HostingManagerMenu menu && menu.managerPos().equals(payload.manager())) {
                ContractService.apply(level, DataCenterApi.accountOf(level, player.getUUID()), payload.contract(), payload.action());
                sendSnapshot(player, level, payload.manager());
            }
        });
        registrar.playToServer(DeliveryChange.TYPE, DeliveryChange.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level
                    && player.containerMenu instanceof HostingManagerMenu menu && menu.managerPos().equals(payload.manager())) {
                DeliveryService.apply(player, level, DataCenterApi.accountOf(level, player.getUUID()), payload.delivery(),
                        payload.action());
                sendSnapshot(player, level, payload.manager());
            }
        });
        registrar.playToServer(StartupChange.TYPE, StartupChange.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level
                    && player.containerMenu instanceof HostingManagerMenu menu && menu.managerPos().equals(payload.manager())) {
                StartupService.apply(level, DataCenterApi.accountOf(level, player.getUUID()), payload.action());
                sendSnapshot(player, level, payload.manager());
            }
        });
        registrar.playToServer(CampaignBooking.TYPE, CampaignBooking.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level
                    && player.containerMenu instanceof HostingManagerMenu menu && menu.managerPos().equals(payload.manager())) {
                Campaigns.book(level, DataCenterApi.accountOf(level, player.getUUID()), payload.campaign());
                sendSnapshot(player, level, payload.manager());
            }
        });
        registrar.playToServer(PriceChange.TYPE, PriceChange.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level
                    && player.containerMenu instanceof HostingManagerMenu menu && menu.managerPos().equals(payload.manager())) {
                MarketService.changePrice(level, DataCenterApi.accountOf(level, player.getUUID()), payload.product(), payload.delta());
                sendSnapshot(player, level, payload.manager());
            }
        });
    }

    static void sendSnapshot(ServerPlayer player, ServerLevel level, BlockPos manager) {
        PacketDistributor.sendToPlayer(player, new Snapshot(manager, HostingManagerService.snapshot(level, manager, player)));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof HostingManagerMenu menu && player.level() instanceof ServerLevel level) {
                sendSnapshot(player, level, menu.managerPos());
            }
        }
    }
}
