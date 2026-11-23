package de.f4ked.data_center_addon.client;

import com.example.datacenter.client.screen.ShopChromeScreen;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.mixin.AbstractContainerScreenAccessor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Makes Data Center's shop (Shop, Sell and Wallet pages) wider so long part names and stats fit.
 * <p>
 * The window width is set here before every (re-)layout; the shop's content width, which Data Center
 * compiled in as a constant, is widened by the same amount in the {@code Shop*ScreenMixin}s.
 */
@EventBusSubscriber(modid = Data_center_addon.MODID, value = Dist.CLIENT)
public final class ShopWidth {
    /** Data Center's own shop window width. */
    private static final int BASE_WIDTH = 360;
    private static final int SCREEN_MARGIN = 8;

    private static int extra;

    private ShopWidth() {
    }

    /** Pixels added to the shop's width for the open shop screen. */
    public static int extra() {
        return extra;
    }

    @SubscribeEvent
    static void onInit(ScreenEvent.Init.Pre event) {
        if (!(event.getScreen() instanceof ShopChromeScreen screen)) {
            return;
        }
        int wanted = ClientConfig.SPEC.isLoaded() ? ClientConfig.SHOP_EXTRA_WIDTH.get() : 0;
        extra = Math.max(0, Math.min(wanted, screen.width - BASE_WIDTH - SCREEN_MARGIN));
        ((AbstractContainerScreenAccessor) screen).data_center_addon$setImageWidth(BASE_WIDTH + extra);
    }
}
