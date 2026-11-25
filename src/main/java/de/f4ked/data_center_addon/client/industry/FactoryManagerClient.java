package de.f4ked.data_center_addon.client.industry;

import de.f4ked.data_center_addon.industry.manager.FactorySnapshot;
import net.minecraft.core.BlockPos;

/**
 * The figures last received for the open Factory Manager. Plain holder: it is referenced by the payload
 * handler on both sides but only filled on the client.
 */
public final class FactoryManagerClient {
    private static volatile BlockPos pos;
    private static volatile FactorySnapshot snapshot = FactorySnapshot.EMPTY;

    private FactoryManagerClient() {
    }

    public static void receive(BlockPos managerPos, FactorySnapshot figures) {
        pos = managerPos;
        snapshot = figures;
    }

    public static boolean has(BlockPos managerPos) {
        return managerPos.equals(pos);
    }

    public static FactorySnapshot snapshot(BlockPos managerPos) {
        return managerPos.equals(pos) ? snapshot : FactorySnapshot.EMPTY;
    }
}
