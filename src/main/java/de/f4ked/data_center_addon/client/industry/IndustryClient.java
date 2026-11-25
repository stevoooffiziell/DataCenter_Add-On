package de.f4ked.data_center_addon.client.industry;

import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.industry.IndustryContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Screens of the add-on's industry blocks. */
@EventBusSubscriber(modid = Data_center_addon.MODID, value = Dist.CLIENT)
public final class IndustryClient {
    private IndustryClient() {
    }

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(IndustryContent.PURCHASE_TERMINAL_MENU.get(), PurchaseTerminalScreen::new);
    }
}
