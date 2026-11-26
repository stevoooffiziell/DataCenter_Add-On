package de.f4ked.data_center_addon.shop;

import java.util.Locale;

/**
 * The add-on's pre-built servers: one model of each line per quarter, composed from the parts on the
 * shelf at that date ({@link AddonPrebuilts}).
 *
 * @param markup  shop price over the parts and the chassis
 * @param sockets most CPUs the build may take
 * @param costCap a build may cost at most this many times the cheapest complete server of its quarter (0:
 *                no cap), which keeps the early-game lines affordable
 */
public enum PrebuiltLine {
    /** Early game: the most shared-hosting customers per dollar among the cheap builds. */
    STARTER(1.10, 1, 2.0),
    /** Early game: the most shared-hosting customers per watt, for a small power budget. */
    ECO(1.15, 1, 3.0),
    /** Shared web hosting: many customers in one server (customers squared per dollar), for full racks. */
    WEB(1.25, 2, 0),
    /** VPS: the most virtual servers per dollar. */
    VPS(1.25, 2, 0),
    /** Game servers: the most game slots per dollar. */
    GAME(1.25, 2, 0),
    /** Dedicated rent: the most rent per dollar (data-centre grade chips earn three times as much). */
    DEDICATED(1.30, 2, 0),
    /** Storage rent: the most gigabytes per dollar. */
    STORAGE(1.25, 1, 0);

    public final double markup;
    public final int sockets;
    public final double costCap;

    PrebuiltLine(double markup, int sockets, double costCap) {
        this.markup = markup;
        this.sockets = sockets;
        this.costCap = costCap;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return "item.data_center_addon.prebuilt." + key();
    }

    public String loreKey() {
        return "item.data_center_addon.prebuilt." + key() + ".lore";
    }

    /** English name, used when the language file lacks it. */
    public String fallback() {
        return switch (this) {
            case STARTER -> "Starter Server - %s";
            case ECO -> "Eco Server - %s";
            case WEB -> "Web Hosting Server - %s";
            case VPS -> "VPS Host - %s";
            case GAME -> "Game Server - %s";
            case DEDICATED -> "Dedicated Server - %s";
            case STORAGE -> "Storage Server - %s";
        };
    }

    public boolean earlyGame() {
        return this == STARTER || this == ECO;
    }
}
