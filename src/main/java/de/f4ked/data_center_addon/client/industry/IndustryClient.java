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
        event.register(IndustryContent.MACHINE_MENU.get(), FactoryMachineScreen::new);
        event.register(IndustryContent.CHIP_FAB_MENU.get(), ChipFabScreen::new);
        event.register(IndustryContent.SALES_TERMINAL_MENU.get(), SalesTerminalScreen::new);
        event.register(IndustryContent.HARDWARE_FAB_MENU.get(), HardwareFabScreen::new);
    }
}
