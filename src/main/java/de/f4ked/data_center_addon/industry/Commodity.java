package de.f4ked.data_center_addon.industry;

import de.f4ked.data_center_addon.Config;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.time.LocalDate;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The raw materials the Purchase Terminal sells: what a factory starts from.
 * <p>
 * Every commodity has a price in 1999 and a price curve over the years. Copper and gold follow their real
 * market (copper about ×4 from 2000 to 2011, gold from $280 to $1,800 an ounce), the others rise with
 * general inflation (2.5 % a year). The config multiplier {@code industry.purchasePrices} scales them all.
 */
public enum Commodity {
    RAW_COPPER(() -> Items.RAW_COPPER, 80, Curve.COPPER),
    RAW_IRON(() -> Items.RAW_IRON, 30, Curve.INFLATION),
    RAW_GOLD(() -> Items.RAW_GOLD, 900, Curve.GOLD),
    QUARTZ(() -> Items.QUARTZ, 120, Curve.INFLATION),
    SAND(() -> Items.SAND, 5, Curve.INFLATION),
    REDSTONE(() -> Items.REDSTONE, 150, Curve.INFLATION),
    COAL(() -> Items.COAL, 10, Curve.INFLATION),
    PLASTIC_PELLETS(() -> IndustryContent.PLASTIC_PELLETS.get(), 30, Curve.OIL);

    private final Supplier<Item> item;
    /** Price of one item in 1999, in US cents. */
    private final double cents1999;
    private final Curve curve;

    Commodity(Supplier<Item> item, double cents1999, Curve curve) {
        this.item = item;
        this.cents1999 = cents1999;
        this.curve = curve;
    }

    public Item item() {
        return item.get();
    }

    /** Price of one item in 1999, in US cents, before the config multiplier. */
    public double cents1999() {
        return cents1999;
    }

    /** How the price moves: copper, gold, oil or inflation. */
    public String curveName() {
        return curve.name().toLowerCase(Locale.ROOT);
    }

    public String key() {
        return "data_center_addon.industry.commodity." + name().toLowerCase(Locale.ROOT);
    }

    /** Price of one item on that date, in US cents (at least 1). */
    public long priceCents(LocalDate date) {
        return Math.max(1, Math.round(cents1999 * curve.factor(date) * Config.purchasePrices()));
    }

    public static Commodity byItem(Item item) {
        for (Commodity c : values()) {
            if (c.item() == item) {
                return c;
            }
        }
        return null;
    }

    /** How a price moves from 1999 on: a factor per year, interpolated, flat after the last year. */
    enum Curve {
        INFLATION(null),
        // LME copper, yearly average relative to 1999 (about $1,570 a tonne)
        COPPER(new double[]{1999, 1.00, 2000, 1.15, 2001, 1.00, 2002, 1.00, 2003, 1.13, 2004, 1.82, 2005, 2.33, 2006, 4.29,
                2007, 4.55, 2008, 4.42, 2009, 3.30, 2010, 4.85, 2011, 5.63, 2012, 5.07, 2013, 4.66, 2014, 4.35, 2015, 3.53,
                2016, 3.10, 2017, 3.94, 2018, 4.18, 2019, 3.85, 2020, 3.94, 2021, 5.97, 2022, 5.59, 2023, 5.45, 2024, 5.85,
                2025, 6.10}),
        // gold, yearly average relative to 1999 (about $279 an ounce)
        GOLD(new double[]{1999, 1.00, 2000, 1.00, 2001, 0.97, 2002, 1.11, 2003, 1.30, 2004, 1.47, 2005, 1.60, 2006, 2.17,
                2007, 2.50, 2008, 3.12, 2009, 3.49, 2010, 4.39, 2011, 5.63, 2012, 5.98, 2013, 5.05, 2014, 4.54, 2015, 4.15,
                2016, 4.48, 2017, 4.48, 2018, 4.52, 2019, 5.00, 2020, 6.37, 2021, 6.43, 2022, 6.45, 2023, 6.95, 2024, 8.30,
                2025, 10.5}),
        // plastics follow crude oil, damped
        OIL(new double[]{1999, 1.00, 2000, 1.25, 2001, 1.15, 2002, 1.15, 2003, 1.25, 2004, 1.45, 2005, 1.75, 2006, 1.95,
                2007, 2.05, 2008, 2.45, 2009, 1.80, 2010, 2.10, 2011, 2.50, 2012, 2.50, 2013, 2.45, 2014, 2.30, 2015, 1.60,
                2016, 1.45, 2017, 1.65, 2018, 1.90, 2019, 1.80, 2020, 1.50, 2021, 1.95, 2022, 2.45, 2023, 2.15, 2024, 2.10,
                2025, 2.00});

        private final double[] anchors;

        Curve(double[] anchors) {
            this.anchors = anchors;
        }

        double factor(LocalDate date) {
            double year = date.getYear() + (date.getDayOfYear() - 1) / 365.25;
            if (anchors == null) {
                return Math.pow(1.025, Math.max(0, year - 1999));
            }
            if (year <= anchors[0]) {
                return anchors[1];
            }
            for (int i = 0; i + 2 < anchors.length; i += 2) {
                if (year < anchors[i + 2]) {
                    double f = (year - anchors[i]) / (anchors[i + 2] - anchors[i]);
                    return anchors[i + 1] * (1 - f) + anchors[i + 3] * f;
                }
            }
            return anchors[anchors.length - 1];
        }
    }
}
