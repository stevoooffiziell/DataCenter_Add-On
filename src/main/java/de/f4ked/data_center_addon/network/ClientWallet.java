package de.f4ked.data_center_addon.network;

/**
 * The local player's wallet as last sent by the server ({@link WalletSyncPayload}); null until the
 * first sync or after leaving a world. Plain holder, safe to load on a dedicated server.
 */
public final class ClientWallet {
    private static volatile WalletSyncPayload current;

    private ClientWallet() {
    }

    public static WalletSyncPayload get() {
        return current;
    }

    static void set(WalletSyncPayload payload) {
        current = payload;
    }

    public static void clear() {
        current = null;
    }
}
