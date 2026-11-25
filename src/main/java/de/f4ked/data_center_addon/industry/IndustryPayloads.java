package de.f4ked.data_center_addon.industry;

import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.hosting.HostingManagerPayloads;
import de.f4ked.data_center_addon.industry.hardware.HardwareFabBlockEntity;
import de.f4ked.data_center_addon.industry.hardware.HardwareFabMenu;
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

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(HostingManagerPayloads.NETWORK_VERSION);
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
