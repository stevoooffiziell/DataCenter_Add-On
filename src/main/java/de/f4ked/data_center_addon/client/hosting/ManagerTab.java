package de.f4ked.data_center_addon.client.hosting;

import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.client.gui.GuiGraphics;

/** One tab of the Hosting Manager screen; draws itself into the content area every frame. */
interface ManagerTab {
    void render(GuiGraphics g, HostingSnapshot s, int x, int y, int w, int h);
}
