package de.f4ked.data_center_addon.jei;

import com.example.datacenter.shop.ShopCatalog;
import com.example.datacenter.shop.ShopEntry;
import de.f4ked.data_center_addon.industry.IndustryContent;
import de.f4ked.data_center_addon.industry.hardware.HardwarePlan;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hardware Fab, one page per kind of part: the components it uses (with their share of the material
 * value), the parts of that kind (cycling), and how much material a part needs.
 */
final class HardwareCategory implements IRecipeCategory<HardwarePlan.Group> {
    static final RecipeType<HardwarePlan.Group> TYPE = RecipeType.create("data_center_addon", "hardware_fab", HardwarePlan.Group.class);
    private static final int WIDTH = 150, HEIGHT = 44;

    private final IDrawable icon;
    private final IDrawableAnimated arrow;

    HardwareCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(IndustryContent.HARDWARE_FAB_ITEM.get()));
        this.arrow = gui.createAnimatedRecipeArrow(80);
    }

    @Override
    public RecipeType<HardwarePlan.Group> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.data_center_addon.hardware_fab");
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
    public void setRecipe(IRecipeLayoutBuilder builder, HardwarePlan.Group group, IFocusGroup focuses) {
        int x = 1;
        for (int i = 0; i < HardwarePlan.INPUTS.length; i++) {
            double share = group.weight(i);
            if (share <= 0) {
                continue;
            }
            int percent = (int) Math.round(share * 100);
            builder.addSlot(RecipeIngredientRole.INPUT, x, 1).setStandardSlotBackground()
                    .addItemStack(new ItemStack(HardwarePlan.INPUTS[i].item()))
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(
                            "data_center_addon.jei.hardware.share", percent).withStyle(ChatFormatting.GRAY)));
            x += 18;
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 18, 1).setOutputSlotBackground().addItemStacks(parts(group));
    }

    /** Every part of the kind the Shop knows, whatever its release date. */
    static List<ItemStack> parts(HardwarePlan.Group group) {
        List<ItemStack> out = new ArrayList<>();
        for (ShopEntry e : ShopCatalog.entries()) {
            if (e.partId() != null && HardwarePlan.Group.of(e.category()) == group && ShopCatalog.isAvailable(e)) {
                ItemStack stack = e.createStack();
                if (!stack.isEmpty()) {
                    out.add(stack);
                }
            }
        }
        return out;
    }

    @Override
    public void draw(HardwarePlan.Group group, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        arrow.draw(g, WIDTH - 44, 1);
        var font = Minecraft.getInstance().font;
        g.drawString(font, Component.translatable(group.key()), 0, 22, 0xFF404040, false);
        g.drawString(font, Component.translatable("data_center_addon.jei.hardware.note"), 0, 33, 0xFF808080, false);
    }
}
