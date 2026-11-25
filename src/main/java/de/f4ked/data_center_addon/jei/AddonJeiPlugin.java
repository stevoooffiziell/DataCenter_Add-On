package de.f4ked.data_center_addon.jei;

import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.hosting.Money;
import de.f4ked.data_center_addon.industry.Commodity;
import de.f4ked.data_center_addon.industry.IndustryContent;
import de.f4ked.data_center_addon.industry.Product;
import de.f4ked.data_center_addon.industry.chipfab.ChipFabBlockEntity;
import de.f4ked.data_center_addon.industry.chipfab.ChipFabStructure;
import de.f4ked.data_center_addon.industry.hardware.HardwarePlan;
import de.f4ked.data_center_addon.industry.machine.MachineKind;
import de.f4ked.data_center_addon.industry.machine.MachineRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * JEI pages for the add-on's industry: a page per factory machine and for the chip production line, the
 * Hardware Fab by kind of part, what the Purchase Terminal sells and what the Sales Terminal buys, and
 * info pages for the multiblock parts and the Factory Manager.
 * <p>
 * Loaded by JEI itself ({@link JeiPlugin}); the add-on runs without JEI.
 */
@JeiPlugin
public class AddonJeiPlugin implements IModPlugin {
    private static final Map<MachineKind, RecipeType<MachineCategory.Entry>> MACHINES = new EnumMap<>(MachineKind.class);
    private static final RecipeType<MachineCategory.Entry> CHIP_FAB = type("chip_fab");
    private static final RecipeType<TradeCategory.Entry> PURCHASE = RecipeType.create(Data_center_addon.MODID, "purchase_terminal",
            TradeCategory.Entry.class);
    private static final RecipeType<TradeCategory.Entry> SALES = RecipeType.create(Data_center_addon.MODID, "sales_terminal",
            TradeCategory.Entry.class);

    static {
        for (MachineKind kind : MachineKind.values()) {
            MACHINES.put(kind, type(kind.id()));
        }
    }

    private static RecipeType<MachineCategory.Entry> type(String path) {
        return RecipeType.create(Data_center_addon.MODID, path, MachineCategory.Entry.class);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        for (MachineKind kind : MachineKind.values()) {
            registration.addRecipeCategories(new MachineCategory(gui, MACHINES.get(kind),
                    Component.translatable("block.data_center_addon." + kind.id()), IndustryContent.MACHINES.get(kind).get(), 2));
        }
        registration.addRecipeCategories(new MachineCategory(gui, CHIP_FAB, Component.translatable("data_center_addon.jei.chip_fab"),
                IndustryContent.CHIP_FAB_CONTROLLER.get(), ChipFabBlockEntity.INPUTS));
        registration.addRecipeCategories(new HardwareCategory(gui));
        registration.addRecipeCategories(new TradeCategory(gui, PURCHASE, Component.translatable("block.data_center_addon.purchase_terminal"),
                IndustryContent.PURCHASE_TERMINAL.get(), true));
        registration.addRecipeCategories(new TradeCategory(gui, SALES, Component.translatable("block.data_center_addon.sales_terminal"),
                IndustryContent.SALES_TERMINAL.get(), false));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (MachineKind kind : MachineKind.values()) {
            List<MachineCategory.Entry> entries = new ArrayList<>();
            for (MachineRecipe r : kind.recipes()) {
                entries.add(new MachineCategory.Entry(r, null));
            }
            registration.addRecipes(MACHINES.get(kind), entries);
        }
        Component chipNote = Component.translatable("data_center_addon.jei.chip_fab.note", ChipFabStructure.MAX_UNITS);
        registration.addRecipes(CHIP_FAB, ChipFabBlockEntity.RECIPES.stream().map(r -> new MachineCategory.Entry(r, chipNote)).toList());
        registration.addRecipes(HardwareCategory.TYPE, List.of(HardwarePlan.Group.values()));

        List<TradeCategory.Entry> buy = new ArrayList<>();
        for (Commodity c : Commodity.values()) {
            buy.add(new TradeCategory.Entry(new ItemStack(c.item()),
                    Component.translatable("data_center_addon.jei.price1999", Money.usd(Math.round(c.cents1999()))),
                    Component.translatable("data_center_addon.jei.curve." + c.curveName())));
        }
        registration.addRecipes(PURCHASE, buy);
        List<TradeCategory.Entry> sell = new ArrayList<>();
        for (Product p : Product.values()) {
            sell.add(new TradeCategory.Entry(new ItemStack(p.item()),
                    Component.translatable("data_center_addon.jei.price1999", Money.usd(Math.round(p.cents1999()))),
                    Component.translatable("data_center_addon.jei.sales.note")));
        }
        registration.addRecipes(SALES, sell);

        registration.addIngredientInfo(IndustryContent.CLEANROOM_WALL.get(), Component.translatable("data_center_addon.jei.info.chip_fab",
                ChipFabStructure.MIN_WALLS, ChipFabStructure.WALLS_PER_UNIT, ChipFabStructure.MAX_UNITS));
        registration.addIngredientInfo(IndustryContent.LITHOGRAPHY_UNIT.get(), Component.translatable("data_center_addon.jei.info.chip_fab",
                ChipFabStructure.MIN_WALLS, ChipFabStructure.WALLS_PER_UNIT, ChipFabStructure.MAX_UNITS));
        registration.addIngredientInfo(IndustryContent.FACTORY_MANAGER.get(), Component.translatable("data_center_addon.jei.info.factory_manager"));
        registration.addIngredientInfo(IndustryContent.PLASTIC_PELLETS.get(), Component.translatable("data_center_addon.jei.info.plastic_pellets"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (MachineKind kind : MachineKind.values()) {
            registration.addRecipeCatalysts(MACHINES.get(kind), IndustryContent.MACHINES.get(kind).get());
        }
        registration.addRecipeCatalysts(CHIP_FAB, IndustryContent.CHIP_FAB_CONTROLLER.get(), IndustryContent.LITHOGRAPHY_UNIT.get(),
                IndustryContent.CLEANROOM_WALL.get());
        registration.addRecipeCatalysts(HardwareCategory.TYPE, IndustryContent.HARDWARE_FAB.get());
        registration.addRecipeCatalysts(PURCHASE, IndustryContent.PURCHASE_TERMINAL.get());
        registration.addRecipeCatalysts(SALES, IndustryContent.SALES_TERMINAL.get());
    }
}
