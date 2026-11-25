package de.f4ked.data_center_addon.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
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

/**
 * A terminal page: what the Purchase Terminal sells (the item as output) or what the Sales Terminal buys
 * (the item as input), with its price in 1999 and how the price moves.
 */
final class TradeCategory implements IRecipeCategory<TradeCategory.Entry> {
    private static final int WIDTH = 150, HEIGHT = 30;

    /** One item with its two lines of text. */
    record Entry(ItemStack stack, Component price, Component curve) {
    }

    private final RecipeType<Entry> type;
    private final Component title;
    private final IDrawable icon;
    private final boolean buying;

    /** @param buying true for the Purchase Terminal (the player buys: the item is an output) */
    TradeCategory(IGuiHelper gui, RecipeType<Entry> type, Component title, ItemLike icon, boolean buying) {
        this.type = type;
        this.title = title;
        this.icon = gui.createDrawableItemStack(new ItemStack(icon));
        this.buying = buying;
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

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
        builder.addSlot(buying ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT, 1, 6)
                .setStandardSlotBackground().addItemStack(entry.stack());
    }

    @Override
    public void draw(Entry entry, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, entry.price(), 24, 5, 0xFF404040, false);
        g.drawString(font, entry.curve(), 24, 17, 0xFF808080, false);
    }
}
