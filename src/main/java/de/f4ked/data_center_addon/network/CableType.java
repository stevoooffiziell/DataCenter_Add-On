package de.f4ked.data_center_addon.network;

import java.time.LocalDate;
import java.util.Locale;

/**
 * The add-on's data cables, modelled on real Ethernet and fibre standards.
 * <p>
 * Capacity is proportional to the real link speed ({@link #COMPUTE_PER_GBIT} compute per tick for
 * 1 Gbit/s, the same as Data Center's twisted-pair stage). Every cable also has the real maximum
 * length of its link: a cable run longer than that falls back to a tenth of its speed, like a link
 * that negotiates down to the next slower standard. Switches start a new run.
 */
public enum CableType {
    CAT3(Medium.COPPER, 0.01, 100, "1991-07-01", 30),
    CAT5(Medium.COPPER, 0.1, 100, "1995-10-01", 50),
    CAT5E(Medium.COPPER, 1, 100, "1999-06-01", 60),
    /** 10GBASE-T only reaches 55 m over Cat 6; longer runs fall back to 1 Gbit/s. */
    CAT6(Medium.COPPER, 10, 55, "2002-06-01", 100),
    CAT6A(Medium.COPPER, 10, 100, "2008-02-01", 150),
    /** 40GBASE-T: 30 m only, meant for the rack row. */
    CAT8(Medium.COPPER, 40, 30, "2016-06-01", 400),
    /** OM3 multimode at 10 Gbit/s (10GBASE-SR): 300 m. */
    OM3(Medium.FIBRE, 10, 300, "2002-06-01", 200),
    /** OM4 multimode at 100 Gbit/s (100GBASE-SR4): 150 m. */
    OM4(Medium.FIBRE, 100, 150, "2009-08-01", 300),
    /** OS2 single-mode at 400 Gbit/s (400GBASE-LR8): 10 km. */
    OS2(Medium.FIBRE, 400, 10_000, "2017-12-06", 500);

    public static final long COMPUTE_PER_GBIT = 250_000L;
    /** Speed of a cable run that is longer than the cable's reach, as a fraction of its full speed. */
    public static final long OVERLENGTH_DIVISOR = 10L;

    public enum Medium { COPPER, FIBRE }

    public final Medium medium;
    public final double gbitPerSecond;
    /** Longest run in blocks (= metres) at full speed. */
    public final int reach;
    public final LocalDate releaseDate;
    /** Shop price per cable block in US cents. */
    public final long priceCents;

    CableType(Medium medium, double gbitPerSecond, int reach, String releaseDate, long priceCents) {
        this.medium = medium;
        this.gbitPerSecond = gbitPerSecond;
        this.reach = reach;
        this.releaseDate = LocalDate.parse(releaseDate);
        this.priceCents = priceCents;
    }

    public String id() {
        return "cable_" + name().toLowerCase(Locale.ROOT);
    }

    /** Compute per tick a run of this cable passes at full speed. */
    public long capacity() {
        return Math.round(gbitPerSecond * COMPUTE_PER_GBIT);
    }

    public long overlengthCapacity() {
        return capacity() / OVERLENGTH_DIVISOR;
    }
}
