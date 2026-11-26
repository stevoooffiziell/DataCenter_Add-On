package de.f4ked.data_center_addon.client.hosting;

import de.f4ked.data_center_addon.hosting.reputation.CustomerTier;
import com.example.datacenter.client.screen.GuiText;
import com.example.datacenter.hosting.HostingMode;
import com.example.datacenter.hosting.HostingProduct;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
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

    /** {@code data_center_addon.reputation.<key>} */
    static String reputation(String key) {
        return Component.translatable("data_center_addon.reputation." + key).getString();
    }

    /** {@code data_center_addon.market.<key>} */
    static String market(String key) {
        return Component.translatable("data_center_addon.market." + key).getString();
    }

    /**
     * "Market Shared Web: 4.2K · reachable 900 · you have 310 · next class (Mid-Market): +400", the
     * summary of one product's market.
     */
    static Component marketSummary(HostingSnapshot.MarketRow row) {
        Component product = productName(row.product());
        String pool = GuiText.compactNumber(Math.round(row.pool()));
        String reach = GuiText.compactNumber(Math.round(row.potential()));
        String have = GuiText.compactNumber(Math.round(row.customers()));
        if (row.nextClass() < 0) {
            return Component.translatable("data_center_addon.market.summary.top", product, pool, reach, have);
        }
        return Component.translatable("data_center_addon.market.summary", product, pool, reach, have,
                Component.translatable(CustomerTier.of(row.nextClass()).translationKey()),
                "+" + GuiText.compactNumber(Math.round(row.nextGain())));
    }

    /**
     * "Ruf 94 % (uptime 99.2 %, 1 crash, 0 % overload) · Renown 312 (+40 orders, +12 reviews …)": the
     * reputation in one line; the renown sources are those of the last 30 days, largest first.
     */
    static Component standingSummary(HostingSnapshot.Standing st, int maxSources) {
        Component ruf = Component.translatable("data_center_addon.reputation.summary.ruf", percent((float) st.ruf()),
                String.format(Locale.ROOT, "%.1f %%", st.uptime() * 100), st.crashes(), percent((float) st.overload()));
        StringBuilder sources = new StringBuilder();
        de.f4ked.data_center_addon.hosting.reputation.Renown.Source[] all =
                de.f4ked.data_center_addon.hosting.reputation.Renown.Source.values();
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int i = 0; i < st.recent().size() && i < all.length; i++) {
            if (Math.abs(st.recent().get(i)) >= 0.5) {
                order.add(i);
            }
        }
        order.sort((a, b) -> Double.compare(Math.abs(st.recent().get(b)), Math.abs(st.recent().get(a))));
        for (int k = 0; k < order.size() && k < maxSources; k++) {
            int i = order.get(k);
            double v = st.recent().get(i);
            sources.append(k == 0 ? "" : ", ").append(v > 0 ? "+" : "−").append(Math.round(Math.abs(v))).append(' ')
                    .append(Component.translatable(all[i].key()).getString());
        }
        if (order.size() > maxSources) {
            sources.append(" …");
        }
        Component renown = sources.isEmpty()
                ? Component.translatable("data_center_addon.reputation.summary.renown_plain", Math.round(st.renown()))
                : Component.translatable("data_center_addon.reputation.summary.renown", Math.round(st.renown()), sources.toString());
        return Component.empty().append(ruf).append(" · ").append(renown);
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
