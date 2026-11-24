package de.f4ked.data_center_addon.hosting;

import com.example.datacenter.api.DataCenterApi;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.client.HostingManagerClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
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

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
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
