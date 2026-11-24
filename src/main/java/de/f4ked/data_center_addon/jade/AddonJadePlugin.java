package de.f4ked.data_center_addon.jade;

import com.example.datacenter.hosting.HostingRackBlock;
import de.f4ked.data_center_addon.hosting.HostingManagerBlock;
import de.f4ked.data_center_addon.network.DataCableBlock;
import de.f4ked.data_center_addon.network.NetworkSwitchBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade look-at tooltips: what a hosting rack sells and earns, how busy a switch's network is, how long a
 * cable run is against its reach, and what the racks behind a Hosting Manager bring in.
 * <p>
 * Jade finds this class by its annotation and only when Jade is installed; nothing else in the add-on
 * refers to it, so the add-on runs without Jade.
 */
@WailaPlugin
public class AddonJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(HostingRackProvider.INSTANCE, HostingRackBlock.class);
        registration.registerBlockDataProvider(SwitchProvider.INSTANCE, NetworkSwitchBlock.class);
        registration.registerBlockDataProvider(CableProvider.INSTANCE, DataCableBlock.class);
        registration.registerBlockDataProvider(ManagerProvider.INSTANCE, HostingManagerBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(HostingRackProvider.INSTANCE, HostingRackBlock.class);
        registration.registerBlockComponent(SwitchProvider.INSTANCE, NetworkSwitchBlock.class);
        registration.registerBlockComponent(CableProvider.INSTANCE, DataCableBlock.class);
        registration.registerBlockComponent(ManagerProvider.INSTANCE, HostingManagerBlock.class);
    }
}
