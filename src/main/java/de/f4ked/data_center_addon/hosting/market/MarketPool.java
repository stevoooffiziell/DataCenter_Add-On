package de.f4ked.data_center_addon.hosting.market;

import com.example.datacenter.hosting.HostingGrowth;
import com.example.datacenter.hosting.HostingMode;
import com.example.datacenter.hosting.HostingProduct;

import java.time.LocalDate;

/**
 * The market of each hosting product: how many potential customers there are in the world.
 * <p>
 * Up to 2008 Data Center's own demand curve sets the size: its customer book settles where new orders
 * and cancellations balance ({@link HostingGrowth#equilibrium} at 0 reputation), and the pool is
 * {@link #POOL_FACTOR} times that. After 2008 Data Center's curve stays flat, so the pool grows on with
 * the number of internet users. Segments that grow with launches in the world (game servers, streaming)
 * raise it further ({@link SegmentPools}).
 */
public final class MarketPool {
    /** The pool is this many times Data Center's own customer equilibrium at 0 reputation. */
    public static final double POOL_FACTOR = 5.0;

    /** The year Data Center's demand curve stops changing. */
    private static final LocalDate CURVE_END = LocalDate.of(2008, 12, 31);

    /** Internet users worldwide in millions, mid-year, 1999 to 2025 (ITU estimates, rounded). */
    private static final double[] USERS = {
            280, 410, 500, 660, 780, 910, 1020, 1150, 1370, 1550, 1750, 2000, 2200, 2500, 2700, 2900,
            3100, 3400, 3600, 3900, 4100, 4600, 4900, 5300, 5400, 5500, 5600};
    private static final int FIRST_YEAR = 1999;

    private MarketPool() {
    }

    /** Whether the product can be sold on that date (VPS from 2002, game servers from June 1999). */
    public static boolean exists(HostingProduct product, LocalDate date) {
        return product.mode().existsOn(date) && (product.mode() != HostingMode.SHARED || product.offer().existsOn(date));
    }

    /** Internet users worldwide in millions on that date, interpolated between the mid-year values. */
    public static double internetUsers(LocalDate date) {
        double year = date.getYear() + (date.getDayOfYear() - 1) / 365.25 - 0.5;
        double i = year - FIRST_YEAR;
        if (i <= 0) {
            return USERS[0];
        }
        if (i >= USERS.length - 1) {
            return USERS[USERS.length - 1];
        }
        int lo = (int) Math.floor(i);
        double f = i - lo;
        return USERS[lo] * (1 - f) + USERS[lo + 1] * f;
    }

    /** Growth of the internet since Data Center's demand curve ended (1 up to the end of 2008). */
    public static double internetGrowth(LocalDate date) {
        return date.isAfter(CURVE_END) ? internetUsers(date) / internetUsers(CURVE_END) : 1.0;
    }

    /** Potential customers of the product in the whole market on that date. */
    public static double pool(HostingProduct product, LocalDate date) {
        if (!exists(product, date)) {
            return 0;
        }
        return HostingGrowth.equilibrium(product, date, 0) * POOL_FACTOR * internetGrowth(date)
                * SegmentPools.poolFactor(product, date);
    }
}
