package de.f4ked.data_center_addon.hosting.market;

import com.example.datacenter.hosting.HostingDemand;
import com.example.datacenter.hosting.HostingProduct;
import net.minecraft.world.level.Level;

import java.time.LocalDate;

/**
 * Market events: the dotcom boom and crash, the Web 2.0 boom and the financial crisis. They change the
 * size of the markets, visibly in the Market tab.
 * <p>
 * Data Center already has a market mood for worlds that start before 2009 ({@link HostingDemand}: 1.0 in
 * 1999, a high in 2000, a low of 0.63 in 2002/03, 0.95 from 2005, a dip in 2008). It multiplies the
 * income of every rack, hidden. With the market model on, the add-on moves that mood from the income to
 * the pools, and adds the Web 2.0 boom on top. Colocation stays out of it, as in Data Center.
 */
public final class MarketEvents {
    private static final LocalDate CRASH_BOTTOM = LocalDate.of(2002, 10, 9);
    private static final LocalDate WEB20_START = LocalDate.of(2004, 10, 5);
    private static final LocalDate WEB20_PEAK = LocalDate.of(2006, 6, 1);
    private static final LocalDate WEB20_FADE = LocalDate.of(2007, 12, 31);
    private static final LocalDate LEHMAN = LocalDate.of(2008, 9, 15);
    private static final LocalDate CRISIS_END = LocalDate.of(2009, 12, 31);
    /** Extra size of the markets at the height of the Web 2.0 boom. */
    static final double WEB20_BOOST = 0.12;

    /** The phases the Market tab names; the name gives {@code data_center_addon.market.event.<name>}. */
    public enum Phase {
        NONE, DOTCOM_BOOM, DOTCOM_CRASH, RECOVERY, WEB20_BOOM, FINANCIAL_CRISIS
    }

    private MarketEvents() {
    }

    /** Factor on the product's pool from the market events (1 = none). */
    public static double factor(Level level, HostingProduct product, LocalDate date) {
        if (product == HostingProduct.COLOCATION) {
            return 1.0;
        }
        return mood(level, date) * web20(date);
    }

    /** Data Center's market mood (only in worlds that start before 2009). */
    static double mood(Level level, LocalDate date) {
        return HostingDemand.factor(level, date);
    }

    /** The Web 2.0 boom: up from October 2004 to mid-2006, full until the end of 2007, gone by Lehman. */
    static double web20(LocalDate date) {
        if (date.isBefore(WEB20_START) || !date.isBefore(LEHMAN)) {
            return 1.0;
        }
        double f;
        if (date.isBefore(WEB20_PEAK)) {
            f = fraction(WEB20_START, WEB20_PEAK, date);
        } else if (date.isBefore(WEB20_FADE)) {
            f = 1.0;
        } else {
            f = 1.0 - fraction(WEB20_FADE, LEHMAN, date);
        }
        return 1.0 + WEB20_BOOST * f;
    }

    private static double fraction(LocalDate from, LocalDate to, LocalDate date) {
        return Math.max(0, Math.min(1, (double) (date.toEpochDay() - from.toEpochDay()) / (to.toEpochDay() - from.toEpochDay())));
    }

    /** The phase to name on that date; NONE when the markets are at their normal size. */
    public static Phase phase(Level level, LocalDate date) {
        double factor = mood(level, date) * web20(date);
        if (Math.abs(factor - 1.0) < 0.005) {
            return Phase.NONE;
        }
        if (date.isBefore(CRASH_BOTTOM)) {
            // the mood stays above 1 for a while after the Nasdaq peak
            return factor > 1 ? Phase.DOTCOM_BOOM : Phase.DOTCOM_CRASH;
        }
        if (date.isBefore(WEB20_START)) {
            return Phase.RECOVERY;
        }
        if (date.isBefore(LEHMAN)) {
            return Phase.WEB20_BOOM;
        }
        if (date.isBefore(CRISIS_END)) {
            return Phase.FINANCIAL_CRISIS;
        }
        return Phase.NONE;
    }
}
