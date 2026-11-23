package de.f4ked.data_center_addon.network;

import com.example.datacenter.trading.CoinMarket;
import com.example.datacenter.wallet.Wallet;
import de.f4ked.data_center_addon.Data_center_addon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends each player's wallet (USD and coin holdings) to their client for the overlay. Data Center
 * itself only syncs the wallet while the Shop is open. Sent on change, checked twice a second.
 */
@EventBusSubscriber(modid = Data_center_addon.MODID)
public record WalletSyncPayload(long usdCents, List<Long> coinUnits) implements CustomPacketPayload {
    public static final Type<WalletSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "wallet_sync"));
    public static final StreamCodec<ByteBuf, WalletSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, WalletSyncPayload::usdCents,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(64)), WalletSyncPayload::coinUnits,
            WalletSyncPayload::new);

    private static final int INTERVAL_TICKS = 10;
    private static final Map<UUID, WalletSyncPayload> LAST_SENT = new ConcurrentHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Holdings of one coin in its smallest unit, 0 if unknown. */
    public long units(CoinMarket coin) {
        int index = coin.ordinal();
        return index < coinUnits.size() ? coinUnits.get(index) : 0;
    }

    static WalletSyncPayload of(Wallet wallet) {
        List<Long> units = new ArrayList<>();
        for (CoinMarket coin : CoinMarket.values()) {
            units.add(wallet.units(coin));
        }
        return new WalletSyncPayload(wallet.getUsdCents(), units);
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, STREAM_CODEC,
                (payload, context) -> ClientWallet.set(payload));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!player.connection.hasChannel(TYPE)) {
                continue;
            }
            WalletSyncPayload payload = of(Wallet.of(player));
            if (!payload.equals(LAST_SENT.get(player.getUUID()))) {
                LAST_SENT.put(player.getUUID(), payload);
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    @SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        LAST_SENT.clear();
    }
}
