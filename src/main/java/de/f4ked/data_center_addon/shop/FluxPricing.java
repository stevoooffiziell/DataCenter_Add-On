package de.f4ked.data_center_addon.shop;

import com.example.datacenter.ConfigSync;
import com.example.datacenter.mining.MiningEra;
import com.example.datacenter.shop.ShopEntry;
import net.minecraft.world.level.Level;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Prices of the Flux tab. Data Center would write the Flux devices down like computer parts (30 % a year from
 * their release at the end of 2008), which makes wireless power a bargain in the late game. Instead they get
 * dearer with the game date: the base price ({@link ShopExtras}) until the end of 2008, then
 * {@value #ANNUAL_RISE_PERCENT} % more every year, compounded day by day (about x2.3 in 2014, x5.2 in 2019,
 * x17 in 2026). Data Center's global price multiplier still applies; there is no scarcity, as for every
 * partner tab.
 * <p>
 * Selling back cannot turn a profit: Data Center caps it at the list (base) price and at what was paid.
 */
public final class FluxPricing {
    static final int ANNUAL_RISE_PERCENT = 18;
    /** Base prices hold until this date: the start of Data Center's economy. */
    static final LocalDate BASE_DATE = MiningEra.START_DATE;

    private FluxPricing() {
    }

    /** Whether the entry is one of the Flux tab's. */
    public static boolean owns(ShopEntry entry) {
        return entry.category() == AddonCategories.flux();
    }

    /** Today's price of a Flux entry. */
    public static long priceUsdCents(ShopEntry entry, Level level) {
        return priceUsdCents(entry.priceUsdCents(), MiningEra.currentDate(level));
    }

    static long priceUsdCents(long baseCents, LocalDate today) {
        double years = Math.max(0.0, ChronoUnit.DAYS.between(BASE_DATE, today) / 365.25);
        double factor = Math.pow(1.0 + ANNUAL_RISE_PERCENT / 100.0, years) * ConfigSync.priceMultiplier();
        return Math.max(1L, Math.round(baseCents * factor));
    }
}
