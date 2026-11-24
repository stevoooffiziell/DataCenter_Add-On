package de.f4ked.data_center_addon.network;

import java.util.Locale;

/** Number formats for the network tooltips. */
public final class NetworkText {
    private NetworkText() {
    }

    /** 10 Mbit/s, 1 Gbit/s, 12.8 Tbit/s. */
    public static String speed(double gbit) {
        if (gbit < 1) {
            return trim(gbit * 1000) + " Mbit/s";
        }
        if (gbit >= 1000) {
            return trim(gbit / 1000) + " Tbit/s";
        }
        return trim(gbit) + " Gbit/s";
    }

    public static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }
}
