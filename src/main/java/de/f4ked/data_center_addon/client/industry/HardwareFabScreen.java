package de.f4ked.data_center_addon.client.industry;

import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.client.screen.TechPanel;
import com.example.datacenter.client.screen.TechPanelScreen;
import com.example.datacenter.shop.ShopEntry;
import de.f4ked.data_center_addon.industry.IndustryPayloads;
import de.f4ked.data_center_addon.industry.hardware.HardwareFabBlockEntity;
import de.f4ked.data_center_addon.industry.hardware.HardwareFabMenu;
import de.f4ked.data_center_addon.industry.hardware.HardwarePlan;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Hardware Fab screen. Left: the kinds of part as tabs and the parts on sale today, to choose what to
 * build. Right: the chosen part, what it needs (green when the inputs hold enough), progress and power,
 * the input and output slots and the player's inventory.
 */
public class HardwareFabScreen extends TechPanelScreen<HardwareFabMenu> {
    private static final int W = 478, H = 236;
    private static final int LIST_X = 10, LIST_W = 284, TAB_Y = 28, TAB_H = 13, LIST_Y = 46, ROW_H = 12;
    private static final int REFRESH_FRAMES = 40;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private List<Component> tooltip;
    private HardwarePlan.Group group = HardwarePlan.Group.CPU;
    private List<ShopEntry> parts = List.of();
    private int frames;
    private int scroll;

    public HardwareFabScreen(HardwareFabMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        ResourceLocation selected = menu.selected();
        ShopEntry entry = selected == null ? null : HardwarePlan.entry(selected);
        if (entry != null) {
            HardwarePlan.Group g = HardwarePlan.Group.of(entry.category());
            if (g != null) {
                group = g;
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // drawn in renderBg
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (tooltip != null && (hoveredSlot == null || !hoveredSlot.hasItem())) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        hits.clear();
        tooltip = null;
        if (frames++ % REFRESH_FRAMES == 0 && minecraft != null && minecraft.level != null) {
            parts = HardwarePlan.buildable(minecraft.level, group);
        }
        int x = leftPos, y = topPos;
        TechPanel.background(g, x, y, W, H);
        g.drawString(font, title, x + 10, y + 8, TechPanel.TEXT, true);
        tabs(g, x + LIST_X, y + TAB_Y, mouseX, mouseY);
        list(g, x + LIST_X, y + LIST_Y, y + H - 8, mouseX, mouseY);
        g.fill(x + LIST_X + LIST_W + 5, y + TAB_Y, x + LIST_X + LIST_W + 6, y + H - 10, 0x605AB0E0);
        selection(g, x + 300, y + 8, mouseX, mouseY);
        g.drawString(font, playerInventoryTitle, x + HardwareFabMenu.INV_X, y + HardwareFabMenu.INV_Y - 11, TechPanel.ACCENT, false);
        for (Slot slot : menu.slots) {
            int accent = slot.index < HardwareFabBlockEntity.INPUTS ? TechPanel.INPUT
                    : slot.index < HardwareFabBlockEntity.SLOTS ? TechPanel.OUTPUT : TechPanel.SLOT_BG;
            TechPanel.slot(g, x + slot.x - 1, y + slot.y - 1, accent);
        }
    }

    /** One tab per kind of part. */
    private void tabs(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        HardwarePlan.Group[] all = HardwarePlan.Group.values();
        int w = LIST_W / all.length;
        for (int i = 0; i < all.length; i++) {
            HardwarePlan.Group tab = all[i];
            int tx = x + i * w;
            boolean active = tab == group;
            boolean hover = hover(mouseX, mouseY, tx, y, w - 2, TAB_H);
            g.fill(tx, y, tx + w - 2, y + TAB_H, active ? TechPanel.ACCENT : hover ? 0xFF2A3440 : TechPanel.SLOT_BG);
            g.drawCenteredString(font, GuiText.fit(font, Component.translatable(tab.key()).getString(), w - 6), tx + (w - 2) / 2,
                    y + 3, active ? 0xFF101418 : TechPanel.TEXT);
            hits.add(new Hit(tx, y, w - 2, TAB_H, () -> {
                group = tab;
                scroll = 0;
                frames = 0;
            }));
        }
    }

    /** The parts of the chosen kind on sale today; click one to build it. */
    private void list(GuiGraphics g, int x, int y, int bottom, int mouseX, int mouseY) {
        int visible = (bottom - y) / ROW_H;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, parts.size() - visible)));
        if (parts.isEmpty()) {
            g.drawString(font, Component.translatable("data_center_addon.industry.hardware.none"), x + 4, y + 4, TechPanel.TEXT_MUTED, false);
            return;
        }
        ResourceLocation selected = menu.selected();
        for (int i = scroll; i < parts.size() && i < scroll + visible; i++) {
            ShopEntry e = parts.get(i);
            int ry = y + (i - scroll) * ROW_H;
            boolean isSelected = e.partId().equals(selected);
            boolean hover = hover(mouseX, mouseY, x, ry, LIST_W, ROW_H);
            if (isSelected) {
                g.fill(x - 2, ry, x + LIST_W, ry + ROW_H, 0x405AB0E0);
            } else if (hover) {
                g.fill(x - 2, ry, x + LIST_W, ry + ROW_H, 0x30FFFFFF);
            } else if (i % 2 == 1) {
                g.fill(x - 2, ry, x + LIST_W, ry + ROW_H, 0x14FFFFFF);
            }
            String price = GuiText.compactUsd(e.priceUsdCents());
            int pw = font.width(price);
            String name = e.createStack().getHoverName().getString();
            g.drawString(font, GuiText.fit(font, name, LIST_W - pw - 12), x + 2, ry + 2, isSelected ? TechPanel.TEXT : TechPanel.TEXT_MUTED, false);
            g.drawString(font, price, x + LIST_W - pw - 4, ry + 2, TechPanel.GOOD, false);
            hits.add(new Hit(x, ry, LIST_W, ROW_H, () -> choose(e.partId())));
        }
        if (parts.size() > visible) {
            int trackH = visible * ROW_H;
            int thumbH = Math.max(10, trackH * visible / parts.size());
            int thumbY = y + (trackH - thumbH) * scroll / Math.max(1, parts.size() - visible);
            g.fill(x + LIST_W + 1, y, x + LIST_W + 3, y + trackH, TechPanel.SLOT_BG);
            g.fill(x + LIST_W + 1, thumbY, x + LIST_W + 3, thumbY + thumbH, TechPanel.ACCENT);
        }
    }

    private void choose(ResourceLocation partId) {
        ResourceLocation next = partId.equals(menu.selected()) ? null : partId;
        menu.setSelected(next);
        PacketDistributor.sendToServer(new IndustryPayloads.HardwareSelect(menu.pos(), next));
    }

    /** The chosen part: name, state, material value and time, what it needs, progress and power. */
    private void selection(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        ResourceLocation selected = menu.selected();
        ShopEntry entry = selected == null ? null : HardwarePlan.entry(selected);
        if (entry == null) {
            g.drawString(font, Component.translatable("data_center_addon.industry.hardware.choose"), x, y + 2, TechPanel.TEXT_MUTED, false);
        } else {
            ItemStack stack = entry.createStack();
            g.renderItem(stack, x, y - 2);
            g.drawString(font, GuiText.fit(font, stack.getHoverName().getString(), 150), x + 20, y + 2, TechPanel.TEXT, false);
        }
        HardwareFabBlockEntity.Status[] statuses = HardwareFabBlockEntity.Status.values();
        HardwareFabBlockEntity.Status status = statuses[Math.max(0, Math.min(statuses.length - 1,
                menu.get(HardwareFabBlockEntity.DATA_STATUS)))];
        g.drawString(font, Component.translatable("data_center_addon.industry.hardware.status." + status.name().toLowerCase(Locale.ROOT)),
                x, y + 16, status == HardwareFabBlockEntity.Status.WORKING ? TechPanel.GOOD : TechPanel.WARN, false);
        int total = menu.get(HardwareFabBlockEntity.DATA_TOTAL);
        if (entry != null && total > 0) {
            String info = Component.translatable("data_center_addon.industry.hardware.info", GuiText.compactUsd(menu.materialCents()),
                    String.format(Locale.ROOT, "%.1f", total / 20.0)).getString();
            g.drawString(font, info, x, y + 28, TechPanel.TEXT_MUTED, false);
            if (hover(mouseX, mouseY, x, y + 26, 168, 11)) {
                tooltip = List.of(Component.translatable("data_center_addon.industry.hardware.info.tip", GuiText.compactUsd(entry.priceUsdCents()))
                        .withStyle(ChatFormatting.GRAY));
            }
            needs(g, x, y + 42, mouseX, mouseY);
        }
        // progress and power next to the slots
        int px = x + 104, py = y + HardwareFabMenu.IN_Y - 8 + 20;
        TechPanel.bar(g, px, py, 64, 8, total <= 0 ? 0 : (float) menu.get(HardwareFabBlockEntity.DATA_PROGRESS) / total, TechPanel.ACCENT);
        int energy = menu.get(HardwareFabBlockEntity.DATA_ENERGY);
        int capacity = Math.max(1, menu.get(HardwareFabBlockEntity.DATA_CAPACITY));
        TechPanel.bar(g, px, py + 14, 64, 8, (float) energy / capacity, TechPanel.WARN);
        if (hover(mouseX, mouseY, px, py + 12, 64, 12)) {
            tooltip = List.of(Component.translatable("data_center_addon.industry.machine.energy", energy, capacity));
        } else if (hover(mouseX, mouseY, px, py - 2, 64, 12) && total > 0) {
            tooltip = List.of(Component.translatable("data_center_addon.industry.machine.progress",
                    menu.get(HardwareFabBlockEntity.DATA_PROGRESS) * 100 / total));
        }
    }

    /** One icon per component with the amount needed; green when the inputs hold it. */
    private void needs(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int cx = x;
        for (int i = 0; i < HardwarePlan.INPUTS.length; i++) {
            int need = menu.get(HardwareFabBlockEntity.DATA_COUNTS + i);
            if (need <= 0) {
                continue;
            }
            ItemStack icon = new ItemStack(HardwarePlan.INPUTS[i].item());
            int have = 0;
            for (int s = 0; s < HardwareFabBlockEntity.INPUTS; s++) {
                ItemStack in = menu.slots.get(s).getItem();
                if (in.is(icon.getItem())) {
                    have += in.getCount();
                }
            }
            g.renderItem(icon, cx, y);
            String count = Integer.toString(need);
            g.drawString(font, count, cx + 17, y + 5, have >= need ? TechPanel.GOOD : TechPanel.DANGER, false);
            if (hover(mouseX, mouseY, cx, y, 16 + font.width(count), 16)) {
                tooltip = List.of(icon.getHoverName(), Component.translatable("data_center_addon.industry.hardware.have", have, need)
                        .withStyle(ChatFormatting.GRAY));
            }
            cx += 20 + font.width(count) + 2;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit h : List.copyOf(hits)) {
                if (h.contains(mouseX, mouseY)) {
                    h.action().run();
                    if (minecraft != null) {
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < leftPos + LIST_X + LIST_W + 4 && scrollY != 0) {
            scroll -= (int) Math.signum(scrollY) * 3;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
