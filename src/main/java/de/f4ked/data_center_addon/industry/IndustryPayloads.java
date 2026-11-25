package de.f4ked.data_center_addon.industry;

import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.hosting.HostingManagerPayloads;
import de.f4ked.data_center_addon.industry.hardware.HardwareFabBlockEntity;
import de.f4ked.data_center_addon.industry.hardware.HardwareFabMenu;
import de.f4ked.data_center_addon.industry.manager.FactoryManagerMenu;
import de.f4ked.data_center_addon.industry.manager.FactoryManagerService;
import de.f4ked.data_center_addon.industry.manager.FactorySnapshot;
import de.f4ked.data_center_addon.client.industry.FactoryManagerClient;
import com.example.datacenter.api.DataCenterApi;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

/** Network traffic of the industry blocks that menu buttons cannot carry. */
@EventBusSubscriber(modid = Data_center_addon.MODID)
public final class IndustryPayloads {
    private IndustryPayloads() {
    }

    /** The part the Hardware Fab at {@code pos} should build ({@code null}: none). */
    public record HardwareSelect(BlockPos pos, @Nullable ResourceLocation partId) implements CustomPacketPayload {
        public static final Type<HardwareSelect> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "hardware_select"));
        public static final StreamCodec<FriendlyByteBuf, HardwareSelect> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeBoolean(p.partId != null);
                    if (p.partId != null) {
                        buf.writeResourceLocation(p.partId);
                    }
                },
                buf -> new HardwareSelect(buf.readBlockPos(), buf.readBoolean() ? buf.readResourceLocation() : null));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The figures of an open Factory Manager, to its viewer. */
    public record FactorySnapshotPayload(BlockPos pos, FactorySnapshot snapshot) implements CustomPacketPayload {
        public static final Type<FactorySnapshotPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "factory_snapshot"));
        public static final StreamCodec<FriendlyByteBuf, FactorySnapshotPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    FactorySnapshot.STREAM_CODEC.encode(buf, p.snapshot);
                },
                buf -> new FactorySnapshotPayload(buf.readBlockPos(), FactorySnapshot.STREAM_CODEC.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void sendFactorySnapshot(ServerPlayer player, ServerLevel level, BlockPos pos) {
        PacketDistributor.sendToPlayer(player, new FactorySnapshotPayload(pos,
                FactoryManagerService.snapshot(level, pos, DataCenterApi.accountOf(level, player.getUUID()))));
    }

    /** Sends the figures to every player with a Factory Manager open, once a second. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof FactoryManagerMenu menu && player.level() instanceof ServerLevel level) {
                sendFactorySnapshot(player, level, menu.managerPos());
            }
        }
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(HostingManagerPayloads.NETWORK_VERSION);
        registrar.playToClient(FactorySnapshotPayload.TYPE, FactorySnapshotPayload.STREAM_CODEC,
                (payload, context) -> FactoryManagerClient.receive(payload.pos(), payload.snapshot()));
        registrar.playToServer(HardwareSelect.TYPE, HardwareSelect.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof HardwareFabMenu menu
                    && menu.pos().equals(payload.pos())
                    && player.level().getBlockEntity(payload.pos()) instanceof HardwareFabBlockEntity fab) {
                fab.select(payload.partId());
                menu.setSelected(fab.selected());
            }
        });
    }
}
