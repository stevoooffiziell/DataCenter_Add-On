package de.f4ked.data_center_addon.client;

import com.example.datacenter.trading.CoinMarket;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Client config, stored in {@code config/data_center_addon-client.toml}. Editable in game through
 * Mods > Data Center AddOn > Config; every change is applied to the overlay immediately.
 */
public final class ClientConfig {
    private static final Pattern COLOR = Pattern.compile("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    static final List<String> ALL_COINS = Arrays.stream(CoinMarket.values()).map(c -> c.ticker).toList();

    public enum Anchor implements TranslatableEnum {
        TOP_LEFT(0, 0), TOP_CENTER(1, 0), TOP_RIGHT(2, 0),
        MIDDLE_LEFT(0, 1), MIDDLE_RIGHT(2, 1),
        BOTTOM_LEFT(0, 2), BOTTOM_CENTER(1, 2), BOTTOM_RIGHT(2, 2);

        /** 0 = left/top, 1 = centre, 2 = right/bottom. */
        final int column, row;

        Anchor(int column, int row) {
            this.column = column;
            this.row = row;
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("data_center_addon.overlay.anchor." + name().toLowerCase(Locale.ROOT));
        }
    }

    public enum Style implements TranslatableEnum {
        /** Rounded box with a gradient border, like item tooltips (and Jade). */
        TOOLTIP,
        /** Plain rectangle. */
        FLAT,
        /** Text only. */
        NONE;

        @Override
        public Component getTranslatedName() {
            return Component.translatable("data_center_addon.overlay.style." + name().toLowerCase(Locale.ROOT));
        }
    }

    public static final ModConfigSpec SPEC;

    static final ModConfigSpec.BooleanValue ENABLED;
    static final ModConfigSpec.EnumValue<Anchor> ANCHOR;
    static final ModConfigSpec.IntValue OFFSET_X;
    static final ModConfigSpec.IntValue OFFSET_Y;
    static final ModConfigSpec.DoubleValue SCALE;
    static final ModConfigSpec.BooleanValue HIDE_IN_SCREENS;
    static final ModConfigSpec.BooleanValue HIDE_WITH_DEBUG_SCREEN;

    static final ModConfigSpec.EnumValue<Style> STYLE;
    static final ModConfigSpec.DoubleValue BACKGROUND_OPACITY;
    static final ModConfigSpec.ConfigValue<String> BACKGROUND_COLOR;
    static final ModConfigSpec.ConfigValue<String> BORDER_COLOR_TOP;
    static final ModConfigSpec.ConfigValue<String> BORDER_COLOR_BOTTOM;
    static final ModConfigSpec.ConfigValue<String> TEXT_COLOR;
    static final ModConfigSpec.BooleanValue TEXT_SHADOW;

    static final ModConfigSpec.BooleanValue SHOW_DATE;
    static final ModConfigSpec.ConfigValue<String> DATE_FORMAT;

    static final ModConfigSpec.BooleanValue SHOW_WALLET;
    static final ModConfigSpec.BooleanValue SHOW_COIN_VALUE;

    static final ModConfigSpec.BooleanValue SHOW_PRICES;
    static final ModConfigSpec.ConfigValue<List<? extends String>> COINS;
    static final ModConfigSpec.BooleanValue SHOW_UNLAUNCHED;
    static final ModConfigSpec.BooleanValue COLORED_TICKERS;
    static final ModConfigSpec.BooleanValue SHOW_CHANGE;
    static final ModConfigSpec.IntValue CHANGE_DAYS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("In-game overlay with the Data Center date and crypto prices.").push("overlay");
        ENABLED = b.comment("Show the overlay. Also toggled with a key (Controls > Data Center).")
                .define("enabled", true);
        ANCHOR = b.comment("Screen corner or edge the overlay sticks to.")
                .defineEnum("anchor", Anchor.TOP_LEFT);
        OFFSET_X = b.comment("Horizontal distance from the anchor in GUI pixels (positive = towards the screen centre).")
                .defineInRange("offsetX", 4, -4000, 4000);
        OFFSET_Y = b.comment("Vertical distance from the anchor in GUI pixels (positive = towards the screen centre).")
                .defineInRange("offsetY", 120, -4000, 4000);
        SCALE = b.comment("Size of the overlay.")
                .defineInRange("scale", 1.0, 0.25, 4.0);
        HIDE_IN_SCREENS = b.comment("Hide the overlay while a menu or inventory is open (chat keeps it visible).")
                .define("hideInScreens", false);
        HIDE_WITH_DEBUG_SCREEN = b.comment("Hide the overlay while the F3 debug screen is shown.")
                .define("hideWithDebugScreen", true);
        b.pop();

        b.comment("Look of the overlay. Colours are #RRGGBB or #AARRGGBB.").push("appearance");
        STYLE = b.comment("TOOLTIP: box like an item tooltip, FLAT: plain box, NONE: text only.")
                .defineEnum("style", Style.TOOLTIP);
        BACKGROUND_OPACITY = b.comment("Opacity of the box (0 = invisible, 1 = solid).")
                .defineInRange("backgroundOpacity", 0.55, 0.0, 1.0);
        BACKGROUND_COLOR = b.define("backgroundColor", "#100010", ClientConfig::isColor);
        BORDER_COLOR_TOP = b.comment("Border gradient (TOOLTIP style only).")
                .define("borderColorTop", "#504D3A", ClientConfig::isColor);
        BORDER_COLOR_BOTTOM = b.define("borderColorBottom", "#28007F", ClientConfig::isColor);
        TEXT_COLOR = b.define("textColor", "#FFFFFF", ClientConfig::isColor);
        TEXT_SHADOW = b.define("textShadow", true);
        b.pop();

        b.comment("The in-game date.").push("date");
        SHOW_DATE = b.define("showDate", true);
        DATE_FORMAT = b.comment("Java DateTimeFormatter pattern, in the game's language. Examples:",
                        "\"EEE, d MMM yyyy\" -> Mon, 3 Jan 2011   \"dd.MM.yyyy\" -> 03.01.2011   \"yyyy-MM-dd\" -> 2011-01-03")
                .define("dateFormat", "EEE, d MMM yyyy", ClientConfig::isDatePattern);
        b.pop();

        b.comment("Your Data Center wallet.").push("wallet");
        SHOW_WALLET = b.comment("Show the USD balance of your wallet.")
                .define("showWallet", true);
        SHOW_COIN_VALUE = b.comment("Also show what the coins in your wallet are worth in USD at today's prices (only when you hold any).")
                .define("showCoinValue", true);
        b.pop();

        b.comment("Crypto prices (USD per coin) on the in-game date.").push("prices");
        SHOW_PRICES = b.define("showPrices", true);
        COINS = b.comment("Coins to show, in this order. Allowed: " + String.join(", ", ALL_COINS))
                .defineListAllowEmpty("coins", ALL_COINS, () -> "BTC",
                        o -> o instanceof String s && ALL_COINS.contains(s));
        SHOW_UNLAUNCHED = b.comment("Also list coins that do not trade yet on the in-game date (shown as \"-\").")
                .define("showUnlaunched", true);
        COLORED_TICKERS = b.comment("Colour each ticker in its coin's colour.")
                .define("coloredTickers", true);
        SHOW_CHANGE = b.comment("Show the price change in percent.")
                .define("showChange", true);
        CHANGE_DAYS = b.comment("Period of the price change in in-game days (1 = since yesterday, 7 = since a week ago).")
                .defineInRange("changeDays", 1, 1, 365);
        b.pop();

        SPEC = b.build();
    }

    private ClientConfig() {
    }

    private static boolean isColor(Object o) {
        return o instanceof String s && COLOR.matcher(s.trim()).matches();
    }

    private static boolean isDatePattern(Object o) {
        if (!(o instanceof String s) || s.isBlank()) {
            return false;
        }
        try {
            DateTimeFormatter.ofPattern(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Parses a validated colour; #RRGGBB is fully opaque. */
    static int color(ModConfigSpec.ConfigValue<String> value) {
        String hex = value.get().trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        long parsed = Long.parseLong(hex, 16);
        return hex.length() == 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
    }

    /** Toggles the overlay and writes the change to the config file. */
    static void toggle() {
        ENABLED.set(!ENABLED.get());
        SPEC.save();
    }
}
