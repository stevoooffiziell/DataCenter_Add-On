package de.f4ked.data_center_addon.client;

import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.hosting.HostingContent;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.jetbrains.annotations.Nullable;

/** The figures last received for the open Hosting Manager, and its screen registration. */
@EventBusSubscriber(modid = Data_center_addon.MODID, value = Dist.CLIENT)
public final class HostingManagerClient {
    private static BlockPos manager;
    private static HostingSnapshot snapshot = HostingSnapshot.EMPTY;

    private HostingManagerClient() {
    }

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(HostingContent.MENU.get(), HostingManagerScreen::new);
    }

    public static void receive(BlockPos managerPos, HostingSnapshot received) {
        manager = managerPos;
        snapshot = received;
    }

    /** The snapshot for the given manager, or an empty one until the first arrives. */
    public static HostingSnapshot snapshotFor(@Nullable BlockPos managerPos) {
        return managerPos != null && managerPos.equals(manager) ? snapshot : HostingSnapshot.EMPTY;
    }

    /** Whether any snapshot for the given manager has arrived yet. */
    public static boolean hasSnapshot(BlockPos managerPos) {
        return managerPos.equals(manager);
    }
}
