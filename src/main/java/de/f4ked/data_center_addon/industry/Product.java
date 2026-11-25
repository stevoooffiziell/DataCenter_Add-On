package de.f4ked.data_center_addon.industry;

import de.f4ked.data_center_addon.Config;
import net.minecraft.world.item.Item;

import java.time.LocalDate;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * What the Sales Terminal buys: the materials and components of the factory chain, with a price in 1999.
 * <p>
 * The price follows what the product is made of: the gold share follows the gold market, the copper
 * share the copper market, the plastic share the oil price, the rest inflation ({@link Commodity.Curve}).
 * So a rising gold price does not eat the margin of connectors and chips.
 * <p>
 * Intermediate materials sell for about 1.5 to 2 times what their raw materials cost; finished components
 * (circuit boards, capacitors, connectors, microchips) for 3 to 6 times.
 */
public enum Product {
    SILICON(() -> IndustryContent.SILICON.get(), 250, 0, 0, 0),
    COPPER_FOIL(() -> IndustryContent.COPPER_FOIL.get(), 70, 0, 0.6, 0),
    GOLD_CONTACTS(() -> IndustryContent.GOLD_CONTACTS.get(), 320, 0.7, 0, 0),
    COPPER_WIRE(() -> IndustryContent.COPPER_WIRE.get(), 45, 0, 0.6, 0),
    PLASTIC_PARTS(() -> IndustryContent.PLASTIC_PARTS.get(), 90, 0, 0, 0.5),
    PCB_BLANK(() -> IndustryContent.PCB_BLANK.get(), 100, 0, 0.3, 0),
    WAFER(() -> IndustryContent.WAFER.get(), 160, 0, 0, 0),
    CIRCUIT_BOARD(() -> IndustryContent.CIRCUIT_BOARD.get(), 600, 0, 0.1, 0),
    CAPACITOR(() -> IndustryContent.CAPACITOR.get(), 150, 0, 0.2, 0.2),
    CONNECTOR(() -> IndustryContent.CONNECTOR.get(), 600, 0.4, 0, 0.1),
    MICROCHIP(() -> IndustryContent.MICROCHIP.get(), 1200, 0.25, 0, 0);

    private final Supplier<Item> item;
    /** Price of one item in 1999, in US cents. */
    private final double cents1999;
    /** Shares of the price that follow gold, copper and oil; the rest follows inflation. */
    private final double gold, copper, oil;

    Product(Supplier<Item> item, double cents1999, double gold, double copper, double oil) {
        this.item = item;
        this.cents1999 = cents1999;
        this.gold = gold;
        this.copper = copper;
        this.oil = oil;
    }

    public Item item() {
        return item.get();
    }

    /** Price of one item in 1999, in US cents, before the config multiplier. */
    public double cents1999() {
        return cents1999;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** What one item is worth on that date before the market's demand, in US cents. */
    public double baseCents(LocalDate date) {
        double rest = 1 - gold - copper - oil;
        double factor = gold * Commodity.Curve.GOLD.factor(date) + copper * Commodity.Curve.COPPER.factor(date)
                + oil * Commodity.Curve.OIL.factor(date) + rest * Commodity.Curve.INFLATION.factor(date);
        return cents1999 * factor * Config.salesPrices();
    }

    public static Product byItem(Item item) {
        for (Product p : values()) {
            if (p.item() == item) {
                return p;
            }
        }
        return null;
    }
}
