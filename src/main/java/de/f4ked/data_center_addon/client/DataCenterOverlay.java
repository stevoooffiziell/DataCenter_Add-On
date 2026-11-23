package de.f4ked.data_center_addon.client;

import com.example.datacenter.mining.MiningEra;
import com.example.datacenter.trading.CoinMarket;
import com.example.datacenter.util.UsdFormat;
import com.mojang.blaze3d.platform.InputConstants;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.network.ClientWallet;
import de.f4ked.data_center_addon.network.WalletSyncPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * HUD overlay with Data Center's in-game date and the crypto prices on that date. Position, size,
 * look and content come from {@link ClientConfig}; a key binding toggles it.
 */
@EventBusSubscriber(modid = Data_center_addon.MODID, value = Dist.CLIENT)
public final class DataCenterOverlay {
    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, "overlay");

    static final KeyMapping TOGGLE_KEY = new KeyMapping("key.data_center_addon.toggle_overlay",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.datacenter");

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int COLUMN_GAP = 6;
    private static final int SEPARATOR = 3;
    /** Data Center's wallet green. */
    private static final int WALLET = 0xFF3FBF6F;
    private static final int UP = 0xFF55FF55;
    private static final int DOWN = 0xFFFF5555;
    private static final int NEUTRAL = 0xFFAAAAAA;

    private static final Map<String, Integer> COIN_COLORS = Map.ofEntries(
            Map.entry("BTC", 0xFFF7931A), Map.entry("ETH", 0xFF8C8CF2), Map.entry("KAS", 0xFF49EACB),
            Map.entry("XCH", 0xFF3AAC59), Map.entry("XMR", 0xFFFF6600), Map.entry("LTC", 0xFFBFBBBB),
            Map.entry("DOGE", 0xFFC2A633), Map.entry("ETC", 0xFF3AB83A), Map.entry("ZEC", 0xFFF4B728),
            Map.entry("BCH", 0xFF8DC351), Map.entry("RVN", 0xFF5A73C8), Map.entry("ERG", 0xFFFF5E18));

    private static String cachedPattern;
    private static String cachedLanguage;
    private static DateTimeFormatter cachedFormatter;

    private DataCenterOverlay() {
    }

    private record Row(String ticker, int tickerColor, String price, String change, int changeColor) {
    }

    // ------------------------------------------------------------------ registration

    @SubscribeEvent
    static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, DataCenterOverlay::render);
    }

    @SubscribeEvent
    static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_KEY);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientWallet.clear();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        while (TOGGLE_KEY.consumeClick()) {
            ClientConfig.toggle();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable(ClientConfig.ENABLED.get()
                        ? "data_center_addon.overlay.shown" : "data_center_addon.overlay.hidden"), true);
            }
        }
    }

    // ------------------------------------------------------------------ rendering

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ClientConfig.SPEC.isLoaded() || !ClientConfig.ENABLED.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (ClientConfig.HIDE_IN_SCREENS.get() && mc.screen != null && !(mc.screen instanceof ChatScreen)) {
            return;
        }
        if (ClientConfig.HIDE_WITH_DEBUG_SCREEN.get() && mc.getDebugOverlay().showDebugScreen()) {
            return;
        }

        LocalDate date = MiningEra.currentDate(mc.level);
        String dateText = ClientConfig.SHOW_DATE.get() ? formatDate(mc, date) : null;
        List<Row> rows = ClientConfig.SHOW_PRICES.get() ? rows(date) : List.of();
        List<String[]> wallet = walletLines(date);
        if (dateText == null && rows.isEmpty() && wallet.isEmpty()) {
            return;
        }

        // ---- measure
        Font font = mc.font;
        int walletWidth = 0;
        for (String[] line : wallet) {
            walletWidth = Math.max(walletWidth, font.width(line[0]) + COLUMN_GAP + font.width(line[1]));
        }
        int tickerWidth = 0, priceWidth = 0, changeWidth = 0;
        for (Row row : rows) {
            tickerWidth = Math.max(tickerWidth, font.width(row.ticker));
            priceWidth = Math.max(priceWidth, font.width(row.price));
            if (row.change != null) {
                changeWidth = Math.max(changeWidth, font.width(row.change));
            }
        }
        int tableWidth = rows.isEmpty() ? 0
                : tickerWidth + COLUMN_GAP + priceWidth + (changeWidth > 0 ? COLUMN_GAP + changeWidth : 0);
        int contentWidth = Math.max(Math.max(tableWidth, walletWidth), dateText == null ? 0 : font.width(dateText));
        // sections: date, wallet, prices, with a thin line between two that are shown
        int sections = (dateText == null ? 0 : 1) + (wallet.isEmpty() ? 0 : 1) + (rows.isEmpty() ? 0 : 1);
        int lines = rows.size() + wallet.size() + (dateText == null ? 0 : 1);
        int contentHeight = lines * LINE_HEIGHT - 1 + (sections - 1) * SEPARATOR;
        int width = contentWidth + 2 * PADDING;
        int height = contentHeight + 2 * PADDING;

        // ---- place
        float scale = ClientConfig.SCALE.get().floatValue();
        ClientConfig.Anchor anchor = ClientConfig.ANCHOR.get();
        float screenWidth = graphics.guiWidth(), screenHeight = graphics.guiHeight();
        float x = place(anchor.column, screenWidth, width * scale, ClientConfig.OFFSET_X.get());
        float y = place(anchor.row, screenHeight, height * scale, ClientConfig.OFFSET_Y.get());

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);

        drawBackground(graphics, width, height);

        boolean shadow = ClientConfig.TEXT_SHADOW.get();
        int textColor = ClientConfig.color(ClientConfig.TEXT_COLOR);
        int separatorColor = (textColor & 0x00FFFFFF) | 0x40000000;
        int cy = PADDING;
        int sectionsLeft = sections;
        if (dateText != null) {
            graphics.drawString(font, dateText, PADDING, cy, textColor, shadow);
            cy += LINE_HEIGHT;
            if (--sectionsLeft > 0) {
                graphics.fill(PADDING, cy, PADDING + contentWidth, cy + 1, separatorColor);
                cy += SEPARATOR;
            }
        }
        if (!wallet.isEmpty()) {
            for (String[] line : wallet) {
                graphics.drawString(font, line[0], PADDING, cy, NEUTRAL, shadow);
                graphics.drawString(font, line[1], PADDING + contentWidth - font.width(line[1]), cy, WALLET, shadow);
                cy += LINE_HEIGHT;
            }
            if (--sectionsLeft > 0) {
                graphics.fill(PADDING, cy, PADDING + contentWidth, cy + 1, separatorColor);
                cy += SEPARATOR;
            }
        }
        int priceRight = PADDING + tickerWidth + COLUMN_GAP + priceWidth;
        for (Row row : rows) {
            graphics.drawString(font, row.ticker, PADDING, cy, row.tickerColor, shadow);
            graphics.drawString(font, row.price, priceRight - font.width(row.price), cy, textColor, shadow);
            if (row.change != null) {
                int changeRight = priceRight + COLUMN_GAP + changeWidth;
                graphics.drawString(font, row.change, changeRight - font.width(row.change), cy, row.changeColor, shadow);
            }
            cy += LINE_HEIGHT;
        }

        graphics.pose().popPose();
    }

    /** Position along one axis: 0 = start, 1 = centre, 2 = end; the offset points towards the centre. */
    private static float place(int slot, float screen, float size, int offset) {
        float position = switch (slot) {
            case 0 -> offset;
            case 1 -> (screen - size) / 2f + offset;
            default -> screen - size - offset;
        };
        return Math.max(0, Math.min(position, screen - size));
    }

    private static void drawBackground(GuiGraphics graphics, int width, int height) {
        ClientConfig.Style style = ClientConfig.STYLE.get();
        if (style == ClientConfig.Style.NONE) {
            return;
        }
        double opacity = ClientConfig.BACKGROUND_OPACITY.get();
        int background = withOpacity(ClientConfig.color(ClientConfig.BACKGROUND_COLOR), opacity);
        if (style == ClientConfig.Style.FLAT) {
            graphics.fill(0, 0, width, height, background);
            return;
        }
        // Tooltip shape: body with notched corners plus a one pixel gradient border inside it.
        int borderTop = withOpacity(ClientConfig.color(ClientConfig.BORDER_COLOR_TOP), opacity);
        int borderBottom = withOpacity(ClientConfig.color(ClientConfig.BORDER_COLOR_BOTTOM), opacity);
        graphics.fill(1, 0, width - 1, height, background);
        graphics.fill(0, 1, 1, height - 1, background);
        graphics.fill(width - 1, 1, width, height - 1, background);
        graphics.fillGradient(1, 2, 2, height - 2, borderTop, borderBottom);
        graphics.fillGradient(width - 2, 2, width - 1, height - 2, borderTop, borderBottom);
        graphics.fill(1, 1, width - 1, 2, borderTop);
        graphics.fill(1, height - 2, width - 1, height - 1, borderBottom);
    }

    private static int withOpacity(int argb, double opacity) {
        int alpha = (int) Math.round(((argb >>> 24) & 0xFF) * opacity);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    // ------------------------------------------------------------------ content

    private static String formatDate(Minecraft mc, LocalDate date) {
        String pattern = ClientConfig.DATE_FORMAT.get();
        String language = mc.getLanguageManager().getSelected();
        if (!pattern.equals(cachedPattern) || !language.equals(cachedLanguage)) {
            Locale locale = Locale.forLanguageTag(language.replace('_', '-'));
            try {
                cachedFormatter = DateTimeFormatter.ofPattern(pattern, locale);
            } catch (IllegalArgumentException e) {
                cachedFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
            }
            cachedPattern = pattern;
            cachedLanguage = language;
        }
        return cachedFormatter.format(date);
    }

    /** {label, value} lines for the wallet section; empty before the first sync from the server. */
    private static List<String[]> walletLines(LocalDate date) {
        WalletSyncPayload wallet = ClientWallet.get();
        if (wallet == null) {
            return List.of();
        }
        List<String[]> lines = new ArrayList<>(2);
        if (ClientConfig.SHOW_WALLET.get()) {
            lines.add(new String[]{Component.translatable("data_center_addon.overlay.wallet").getString(),
                    UsdFormat.format(wallet.usdCents())});
        }
        if (ClientConfig.SHOW_COIN_VALUE.get()) {
            double value = 0;
            for (CoinMarket coin : CoinMarket.values()) {
                long units = wallet.units(coin);
                if (units > 0) {
                    value += units / coin.unitsPerCoin * coin.priceOrEarliest(date);
                }
            }
            if (value > 0) {
                lines.add(new String[]{Component.translatable("data_center_addon.overlay.coin_value").getString(),
                        UsdFormat.format(Math.round(value * 100))});
            }
        }
        return lines;
    }

    private static List<Row> rows(LocalDate date) {
        boolean showUnlaunched = ClientConfig.SHOW_UNLAUNCHED.get();
        boolean colored = ClientConfig.COLORED_TICKERS.get();
        boolean showChange = ClientConfig.SHOW_CHANGE.get();
        LocalDate before = date.minusDays(ClientConfig.CHANGE_DAYS.get());
        int textColor = ClientConfig.color(ClientConfig.TEXT_COLOR);

        List<Row> rows = new ArrayList<>();
        for (String ticker : ClientConfig.COINS.get()) {
            CoinMarket coin = coin(ticker);
            if (coin == null) {
                continue;
            }
            int tickerColor = colored ? COIN_COLORS.getOrDefault(ticker, textColor) : textColor;
            OptionalDouble price = coin.priceOn(date);
            if (price.isEmpty() || price.getAsDouble() <= 0) {
                if (showUnlaunched) {
                    rows.add(new Row(ticker, tickerColor, "-", showChange ? "" : null, NEUTRAL));
                }
                continue;
            }
            String change = null;
            int changeColor = NEUTRAL;
            if (showChange) {
                OptionalDouble old = coin.priceOn(before);
                if (old.isPresent() && old.getAsDouble() > 0) {
                    double percent = (price.getAsDouble() / old.getAsDouble() - 1.0) * 100.0;
                    change = formatChange(percent);
                    changeColor = percent > 0.005 ? UP : percent < -0.005 ? DOWN : NEUTRAL;
                } else {
                    change = "";
                }
            }
            rows.add(new Row(ticker, tickerColor, formatPrice(price.getAsDouble()), change, changeColor));
        }
        return rows;
    }

    private static CoinMarket coin(String ticker) {
        for (CoinMarket coin : CoinMarket.values()) {
            if (coin.ticker.equals(ticker)) {
                return coin;
            }
        }
        return null;
    }

    /** $43,210.55 for prices from $1 up, four significant digits below (e.g. $0.08123). */
    static String formatPrice(double usd) {
        int decimals = usd >= 1 ? 2 : Math.min(8, 3 - (int) Math.floor(Math.log10(usd)));
        DecimalFormat format = new DecimalFormat("#,##0." + "0".repeat(decimals), DecimalFormatSymbols.getInstance(Locale.US));
        return "$" + format.format(usd);
    }

    static String formatChange(double percent) {
        String arrow = percent > 0.005 ? "▲" : percent < -0.005 ? "▼" : "";
        double abs = Math.abs(percent);
        String number = abs >= 1000 ? String.format(Locale.US, "%,.0f", abs) : String.format(Locale.US, "%.2f", abs);
        return arrow + number + "%";
    }
}
