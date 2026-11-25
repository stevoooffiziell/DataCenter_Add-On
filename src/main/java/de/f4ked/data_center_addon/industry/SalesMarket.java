package de.f4ked.data_center_addon.industry;

import de.f4ked.data_center_addon.Config;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Market saturation of the Sales Terminal, per account and product, the same way Data Center's timber
 * market works: the more a company sells of a product, the less the buyers pay for the next one.
 * <p>
 * Each product keeps the base value sold recently ({@code sold}, in cents). Demand is
 * {@code floor + (1 − floor) × 0.5^(sold / halfLife)}: at {@code halfLife} sold the price is down to
 * halfway between full and the floor. The sold value fades out by half every {@code salesRecoveryDays}
 * in-game days, so demand comes back. A sale is paid along the curve, so selling one big batch or many
 * small ones gives the same money.
 */
public final class SalesMarket extends SavedData {
    private static final String NAME = "data_center_addon_sales_market";

    private final Map<UUID, double[]> sold = new HashMap<>();
    /** Epoch day the sold values were last faded to; per account. */
    private final Map<UUID, Long> day = new HashMap<>();

    public static SalesMarket get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(SalesMarket::new, SalesMarket::load), NAME);
    }

    private double[] of(UUID account, long today) {
        double[] values = sold.computeIfAbsent(account, a -> new double[Product.values().length]);
        long last = day.getOrDefault(account, today);
        if (today > last) {
            double fade = Math.pow(0.5, (today - last) / Math.max(0.1, Config.salesRecoveryDays()));
            for (int i = 0; i < values.length; i++) {
                values[i] *= fade;
            }
            setDirty();
        }
        day.put(account, today);
        return values;
    }

    private static double halfLife() {
        return Math.max(100, Config.salesHalfLifeUsd() * 100);
    }

    private static double decay(double soldCents) {
        return Math.pow(0.5, soldCents / halfLife());
    }

    /** Current demand of the product, between the floor and 1. */
    public double demand(UUID account, Product product, long today) {
        double floor = Config.salesDemandFloor();
        return floor + (1 - floor) * decay(of(account, today)[product.ordinal()]);
    }

    /**
     * Sells {@code count} items worth {@code baseCents} each before demand; returns what the buyers pay,
     * in cents (fractions included), and books the sale.
     */
    public double sell(UUID account, Product product, double baseCents, int count, long today) {
        if (count <= 0 || baseCents <= 0) {
            return 0;
        }
        double[] values = of(account, today);
        int i = product.ordinal();
        double floor = Config.salesDemandFloor();
        double soldNow = baseCents * count;
        double before = decay(values[i]);
        double after = decay(values[i] + soldNow);
        double pay = soldNow * floor + (1 - floor) * halfLife() / Math.log(2) * (before - after);
        values[i] += soldNow;
        setDirty();
        return pay;
    }

    private static SalesMarket load(CompoundTag tag, HolderLookup.Provider registries) {
        SalesMarket market = new SalesMarket();
        for (Tag t : tag.getList("accounts", Tag.TAG_COMPOUND)) {
            CompoundTag a = (CompoundTag) t;
            if (!a.hasUUID("id")) {
                continue;
            }
            UUID id = a.getUUID("id");
            double[] values = new double[Product.values().length];
            CompoundTag products = a.getCompound("sold");
            for (Product p : Product.values()) {
                values[p.ordinal()] = Math.max(0, products.getDouble(p.id()));
            }
            market.sold.put(id, values);
            market.day.put(id, a.getLong("day"));
        }
        return market;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        sold.forEach((id, values) -> {
            CompoundTag a = new CompoundTag();
            a.putUUID("id", id);
            CompoundTag products = new CompoundTag();
            for (Product p : Product.values()) {
                if (values[p.ordinal()] > 0) {
                    products.putDouble(p.id(), values[p.ordinal()]);
                }
            }
            a.put("sold", products);
            a.putLong("day", day.getOrDefault(id, 0L));
            list.add(a);
        });
        tag.put("accounts", list);
        return tag;
    }
}
