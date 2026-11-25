package de.f4ked.data_center_addon.client.hosting;

import com.example.datacenter.hosting.HostingMode;
import com.example.datacenter.hosting.HostingProduct;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Texts of the Hosting Manager screen: translation shortcuts and names of products and states. */
final class Texts {
    private static final String[] STATUS = {"ok", "no_panel", "no_hypervisor", "moving", "no_server", "no_game_os"};

    private Texts() {
    }

    /** {@code data_center_addon.hosting.<key>} */
    static String hosting(String key) {
        return Component.translatable("data_center_addon.hosting." + key).getString();
    }

    /** {@code data_center_addon.contract.<key>} */
    static String contract(String key) {
        return Component.translatable("data_center_addon.contract." + key).getString();
    }

    /** {@code data_center_addon.delivery.<key>} */
    static String delivery(String key) {
        return Component.translatable("data_center_addon.delivery." + key).getString();
    }

    /** {@code data_center_addon.startup.<key>} */
    static String startup(String key) {
        return Component.translatable("data_center_addon.startup." + key).getString();
    }

    /** {@code data_center_addon.market.<key>} */
    static String market(String key) {
        return Component.translatable("data_center_addon.market." + key).getString();
    }

    /** "5 d." */
    static String days(int days) {
        return days + " " + contract("days_short");
    }

    /** "12.5 %" */
    static String percent(float share) {
        return String.format(Locale.ROOT, "%.1f %%", share * 100);
    }

    /** "+200 %" for a bonus factor of 2.0 */
    static String bonus(double factor) {
        return "+" + Math.round(factor * 100) + " %";
    }

    static String pos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    /** A {@code HostingProduct} ordinal; anything else is dedicated servers. */
    static Component productName(int product) {
        if (product < 0 || product >= HostingProduct.values().length) {
            return Component.translatable(HostingMode.DEDICATED.translationKey());
        }
        return Component.translatable(HostingProduct.values()[product].translationKey());
    }

    /** Whether the product has a fill (shared hosting and VPS). */
    static boolean hasFill(int product) {
        if (product < 0 || product >= HostingProduct.values().length) {
            return false;
        }
        HostingMode mode = HostingProduct.values()[product].mode();
        return mode == HostingMode.SHARED || mode == HostingMode.VPS;
    }

    /** A rack's {@code HostingRackBlockEntity.MODE_*} status. */
    static Component statusName(int status) {
        return Component.translatable("data_center_addon.hosting.status." + (status >= 0 && status < STATUS.length ? STATUS[status] : "ok"));
    }
}
