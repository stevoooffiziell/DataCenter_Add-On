package de.f4ked.data_center_addon.hosting;

import java.util.Locale;

/** Money as the add-on's chat messages show it. */
public final class Money {
    private Money() {
    }

    /** $1,234.56 */
    public static String usd(long cents) {
        return String.format(Locale.ROOT, "$%,.2f", cents / 100.0);
    }
}
