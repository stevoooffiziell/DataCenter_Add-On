package de.f4ked.data_center_addon.industry.hardware;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.shop.ShopCatalog;
import com.example.datacenter.shop.ShopCategory;
import com.example.datacenter.shop.ShopEntry;
import com.example.datacenter.shop.ShopPricing;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.industry.Product;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What the Hardware Fab needs to build a server part: components worth about half the part's current Shop
 * price ({@code industry.hardwareCost}), mixed by the kind of part, a time that grows with the price and
 * a power draw.
 * <p>
 * Component values are what the Sales Terminal pays for them at full demand ({@link Product#baseCents}).
 */
public final class HardwarePlan {
    /** The components the Hardware Fab uses, in the order of its requirement arrays. */
    public static final Product[] INPUTS = {Product.MICROCHIP, Product.CIRCUIT_BOARD, Product.CAPACITOR, Product.CONNECTOR,
            Product.PLASTIC_PARTS, Product.COPPER_WIRE};
    public static final int FE_PER_TICK = 60;
    static final int MIN_TICKS = 100, MAX_TICKS = 1200;

    /** The kinds of part, with the share of each input in their material value (in {@link #INPUTS} order). */
    public enum Group {
        CPU(EnumSet.of(ShopCategory.CPU_AMD, ShopCategory.CPU_INTEL, ShopCategory.CPU_SERVER), 0.60, 0.10, 0.10, 0.20, 0, 0),
        GPU(EnumSet.of(ShopCategory.GPU_NVIDIA, ShopCategory.GPU_AMD, ShopCategory.GPU_DATACENTER, ShopCategory.GPU_MINING),
                0.40, 0.25, 0.15, 0.15, 0.05, 0),
        RAM(EnumSet.of(ShopCategory.RAM), 0.60, 0.30, 0, 0.10, 0, 0),
        STORAGE(EnumSet.of(ShopCategory.STORAGE), 0.25, 0.25, 0.15, 0, 0.20, 0.15),
        PSU(EnumSet.of(ShopCategory.PSU), 0, 0.20, 0.35, 0, 0.15, 0.30),
        MAINBOARD(EnumSet.of(ShopCategory.MAINBOARD, ShopCategory.MAINBOARD_MINING), 0.10, 0.45, 0.20, 0.25, 0, 0),
        FAN(EnumSet.of(ShopCategory.FAN), 0, 0.10, 0, 0, 0.60, 0.30);

        private final Set<ShopCategory> categories;
        private final double[] weights;

        Group(Set<ShopCategory> categories, double... weights) {
            this.categories = categories;
            this.weights = weights;
        }

        public String key() {
            return "data_center_addon.industry.hardware.group." + name().toLowerCase(Locale.ROOT);
        }

        /** Share of the {@link #INPUTS} entry {@code i} in the material value. */
        public double weight(int i) {
            return i >= 0 && i < weights.length ? weights[i] : 0;
        }

        @Nullable
        public static Group of(ShopCategory category) {
            for (Group g : values()) {
                if (g.categories.contains(category)) {
                    return g;
                }
            }
            return null;
        }
    }

    /**
     * What one part needs.
     *
     * @param counts        components per {@link #INPUTS} entry
     * @param materialCents value of those components (what the part's Shop sell-back is based on)
     */
    public record Plan(int[] counts, long materialCents, int ticks) {
    }

    private HardwarePlan() {
    }

    /** The plan for the entry on that date and level (Shop price with depreciation and scarcity). */
    public static Plan plan(ShopEntry entry, Level level, LocalDate date) {
        Group group = Group.of(entry.category());
        long price = ShopPricing.currentPriceUsdCents(entry, level);
        double target = price * 0.5 * Config.hardwareCost();
        int[] counts = new int[INPUTS.length];
        long material = 0;
        for (int i = 0; i < INPUTS.length; i++) {
            double w = group == null ? 0 : group.weights[i];
            if (w <= 0) {
                continue;
            }
            double unit = INPUTS[i].baseCents(date);
            counts[i] = (int) Math.max(1, Math.round(target * w / unit));
            material += Math.round(counts[i] * unit);
        }
        int ticks = (int) Math.max(MIN_TICKS, Math.min(MAX_TICKS, MIN_TICKS + price / 2_000));
        return new Plan(counts, material, ticks);
    }

    /** Whether the Hardware Fab can build the entry now: a server part that is on sale today. */
    public static boolean buildable(ShopEntry entry, Level level) {
        LocalDate date = DataCenterApi.currentDate(level);
        return entry.partId() != null && Group.of(entry.category()) != null && entry.releasedOn(date)
                && !ShopCatalog.retired(entry) && ShopCatalog.isAvailable(entry, level);
    }

    /** The entries the Hardware Fab can build now, by group, in Shop order. */
    public static List<ShopEntry> buildable(Level level, Group group) {
        List<ShopEntry> out = new ArrayList<>();
        for (ShopEntry e : ShopCatalog.entries()) {
            if (Group.of(e.category()) == group && buildable(e, level)) {
                out.add(e);
            }
        }
        return out;
    }

    @Nullable
    public static ShopEntry entry(ResourceLocation partId) {
        for (ShopEntry e : ShopCatalog.entries()) {
            if (partId.equals(e.partId())) {
                return e;
            }
        }
        return null;
    }
}
