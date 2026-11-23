package de.f4ked.data_center_addon.network;

import java.time.LocalDate;

/**
 * Real network switches. Each one ends the cable runs connected to it, so every run counts on its
 * own, and its switching capacity (backplane) limits the sum of all runs. A switch needs power.
 */
public enum SwitchModel {
    /** A hub, not a switch: all ports share one 10 Mbit/s collision domain. */
    HUB_3COM_SUPERSTACK_II("3Com SuperStack II Hub 10", "1993-03-01", 395_00, 0.01, 15),
    CISCO_CATALYST_2924XL("Cisco Catalyst 2924-XL", "1997-06-01", 2_495_00, 3.2, 50),
    CISCO_CATALYST_2950("Cisco Catalyst 2950-24", "2001-01-15", 1_095_00, 8.8, 30),
    CISCO_CATALYST_3750G("Cisco Catalyst 3750G-24TS", "2003-04-01", 7_995_00, 32, 140),
    CISCO_NEXUS_5010("Cisco Nexus 5010", "2008-04-01", 30_000_00, 520, 450),
    ARISTA_7050S_64("Arista 7050S-64", "2012-04-01", 34_000_00, 1_280, 220),
    ARISTA_7060CX_32S("Arista 7060CX-32S", "2016-03-01", 32_000_00, 6_400, 340),
    NVIDIA_SPECTRUM_SN4700("NVIDIA Spectrum SN4700", "2020-06-01", 60_000_00, 12_800, 700);

    public final String displayName;
    public final LocalDate releaseDate;
    public final long priceCents;
    /** Switching capacity in Gbit/s. */
    public final double backplaneGbit;
    /** Power draw in W = FE per tick. */
    public final int watts;

    SwitchModel(String displayName, String releaseDate, long priceCents, double backplaneGbit, int watts) {
        this.displayName = displayName;
        this.releaseDate = LocalDate.parse(releaseDate);
        this.priceCents = priceCents;
        this.backplaneGbit = backplaneGbit;
        this.watts = watts;
    }

    public String id() {
        return "switch_" + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Compute per tick the switch can move in total. */
    public long backplane() {
        return Math.round(backplaneGbit * CableType.COMPUTE_PER_GBIT);
    }
}
