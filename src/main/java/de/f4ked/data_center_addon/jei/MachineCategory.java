package de.f4ked.data_center_addon.jei;

import de.f4ked.data_center_addon.industry.machine.MachineRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.Locale;

/**
 * A recipe page for a factory machine or the chip production line: the inputs (with their amounts), an
 * arrow and the output, and below them the time and power at speed 1.
 */
final class MachineCategory implements IRecipeCategory<MachineCategory.Entry> {
    static final int WIDTH = 124, HEIGHT = 40;

    /** One recipe; {@code note} is an extra line (the chip line's speed rule), or null. */
    record Entry(MachineRecipe recipe, Component note) {
    }

    private final RecipeType<Entry> type;
    private final Component title;
    private final IDrawable icon;
    private final IDrawableStatic slot;
    private final IDrawableAnimated arrow;
    private final int inputs;

    MachineCategory(IGuiHelper gui, RecipeType<Entry> type, Component title, ItemLike icon, int inputs) {
        this.type = type;
        this.title = title;
        this.icon = gui.createDrawableItemStack(new ItemStack(icon));
        this.slot = gui.getSlotDrawable();
        this.arrow = gui.createAnimatedRecipeArrow(60);
        this.inputs = inputs;
    }

    @Override
    public RecipeType<Entry> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    private int arrowX() {
        return inputs * 18 + 6;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
        MachineRecipe r = entry.recipe();
        for (int i = 0; i < r.inputs().size(); i++) {
            MachineRecipe.Input in = r.inputs().get(i);
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + i * 18, 1).setStandardSlotBackground()
                    .addItemStack(new ItemStack(in.item().get(), in.count()));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, arrowX() + 32, 1).setOutputSlotBackground().addItemStack(r.result());
    }

    @Override
    public void draw(Entry entry, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        arrow.draw(g, arrowX(), 1);
        var font = Minecraft.getInstance().font;
        MachineRecipe r = entry.recipe();
        String line = String.format(Locale.ROOT, "%.1f s · %d FE/t", r.ticks() / 20.0, r.fePerTick());
        g.drawString(font, line, 0, 22, 0xFF808080, false);
        if (entry.note() != null) {
            g.drawString(font, entry.note(), 0, 31, 0xFF808080, false);
        }
    }
}
