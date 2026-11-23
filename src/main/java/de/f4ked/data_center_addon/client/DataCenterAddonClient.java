package de.f4ked.data_center_addon.client;

import de.f4ked.data_center_addon.Data_center_addon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client entry point: registers the client config (overlay) and adds a "Config" button for the
 * add-on in the mod list.
 */
@Mod(value = Data_center_addon.MODID, dist = Dist.CLIENT)
public class DataCenterAddonClient {
    public DataCenterAddonClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
