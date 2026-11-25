package de.f4ked.data_center_addon.hosting.market;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.hosting.HostingProduct;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fictional competitors: hosting companies run by the world, not by players. Each sells some of the
 * products and holds a share of their markets. They grow in good times, shrink and go bankrupt in bad
 * ones, and new ones are founded. All accounts share them.
 * <p>
 * A product's competition is the summed share of the competitors that sell it (at most
 * {@link #MAX_COMPETITION}). It scales the potential of every account by
 * {@code (1 − competition) / (1 − NOMINAL)}: at the usual {@link #NOMINAL} competition nothing changes,
 * a bankrupt competitor frees its share, a new one takes some.
 */
public final class Competitors {
    /** The competition at which the potential is unchanged. */
    static final double NOMINAL = 0.25;
    /** The strength a competitor drifts back to in normal times (each sells about 60 % of the products). */
    static final double TARGET = 0.08;
    static final double MAX_COMPETITION = 0.8;
    static final double MIN_STRENGTH = 0.02, MAX_STRENGTH = 0.25;
    static final int START_COUNT = 5;
    static final int MAX_COUNT = 7;

    /** Invented names; none is meant to be a real company. */
    static final List<String> NAMES = List.of(
            "Bitmühle", "Serverscheune", "Wolkenschmiede", "Netzkontor", "Glasfaserei", "Rackfield", "Datenkai",
            "Bytebrook", "Quillnet", "Moorlight Data", "Amberrack", "Pixelhafen", "Lumenkeller", "Portlantern",
            "Kabelkrone", "Ferrolink", "Silverwire", "Fjordrack", "Tannhost", "Orbitstall");

    /** One competitor. {@code products}: bit per {@code HostingProduct} ordinal. */
    public static final class Competitor {
        final int id;
        final String name;
        final int products;
        double strength;
        final long foundedDay;

        Competitor(int id, String name, int products, double strength, long foundedDay) {
            this.id = id;
            this.name = name;
            this.products = products;
            this.strength = strength;
            this.foundedDay = foundedDay;
        }

        public String name() {
            return name;
        }

        public double strength() {
            return strength;
        }

        public boolean sells(HostingProduct product) {
            return (products & (1 << product.ordinal())) != 0;
        }

        public int products() {
            return products;
        }
    }

    private Competitors() {
    }

    /** The summed share of the competitors that sell the product. */
    public static double competition(MarketBook book, HostingProduct product) {
        double sum = 0;
        for (Competitor c : book.competitors()) {
            if (c.sells(product)) {
                sum += c.strength;
            }
        }
        return Math.min(MAX_COMPETITION, sum);
    }

    /** Factor on every account's potential of the product (1 at the usual competition). */
    public static double factor(MarketBook book, HostingProduct product) {
        if (!book.competitorsSeeded()) {
            return 1.0;
        }
        return (1 - competition(book, product)) / (1 - NOMINAL);
    }

    // ------------------------------------------------------------------ daily

    /**
     * Moves the competitors on to today: seeds them on the first day, then for every in-game day since
     * the last one lets them grow or shrink, go bankrupt and be founded. Only today's changes are announced.
     */
    static void update(ServerLevel level, MarketBook book) {
        LocalDate date = DataCenterApi.currentDate(level);
        long today = date.toEpochDay();
        RandomSource random = level.getRandom();
        if (!book.competitorsSeeded()) {
            for (int i = 0; i < START_COUNT; i++) {
                found(book, random, date, TARGET * (0.6 + 0.8 * random.nextDouble()));
            }
            book.seedCompetitors(today);
            book.setDirty();
            return;
        }
        if (today <= book.competitorsDay()) {
            return;
        }
        long days = Math.min(60, today - book.competitorsDay());
        for (long i = days - 1; i >= 0; i--) {
            LocalDate day = date.minusDays(i);
            day(level, book, random, day, i == 0);
        }
        book.seedCompetitors(today);
        book.setDirty();
    }

    private static void day(ServerLevel level, MarketBook book, RandomSource random, LocalDate day, boolean announce) {
        double mood = MarketEvents.mood(level, day) * MarketEvents.web20(day);
        List<Competitor> gone = new ArrayList<>();
        for (Competitor c : book.competitors()) {
            // a random walk that leans up in good times and down in bad ones, and back to TARGET
            double drift = 0.004 * (mood - 0.9) + 0.003 * Math.log(TARGET / c.strength);
            c.strength *= Math.exp(random.nextGaussian() * 0.02 + drift);
            c.strength = Math.max(MIN_STRENGTH, Math.min(MAX_STRENGTH, c.strength));
            double bankrupt = 0.0004 + 0.006 * Math.max(0, 0.85 - mood) + (c.strength <= MIN_STRENGTH * 1.25 ? 0.004 : 0);
            if (random.nextDouble() < bankrupt) {
                gone.add(c);
            }
        }
        for (Competitor c : gone) {
            book.competitors().remove(c);
            if (announce) {
                announce(level.getServer(), Component.translatable("data_center_addon.market.news.bankrupt", c.name,
                        products(c.products), percent(c.strength)).withStyle(ChatFormatting.GREEN));
            }
        }
        double founding = 0.002 * Math.max(0, mood - 0.7) / 0.3;
        if (book.competitors().size() < MAX_COUNT && random.nextDouble() < founding) {
            Competitor c = found(book, random, day, 0.03 + 0.04 * random.nextDouble());
            if (c != null && announce) {
                announce(level.getServer(), Component.translatable("data_center_addon.market.news.founded", c.name,
                        products(c.products), percent(c.strength)).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** A new competitor with a free name, selling some of the products that exist on that date. */
    private static Competitor found(MarketBook book, RandomSource random, LocalDate date, double strength) {
        List<String> free = new ArrayList<>(NAMES);
        for (Competitor c : book.competitors()) {
            free.remove(c.name);
        }
        if (free.isEmpty()) {
            return null;
        }
        int products = 0;
        List<HostingProduct> existing = new ArrayList<>();
        for (HostingProduct p : HostingProduct.values()) {
            if (MarketPool.exists(p, date)) {
                existing.add(p);
                if (random.nextDouble() < 0.6) {
                    products |= 1 << p.ordinal();
                }
            }
        }
        if (products == 0 && !existing.isEmpty()) {
            products = 1 << existing.get(random.nextInt(existing.size())).ordinal();
        }
        Competitor c = new Competitor(book.nextCompetitorId(), free.get(random.nextInt(free.size())), products,
                Math.max(MIN_STRENGTH, Math.min(MAX_STRENGTH, strength)), date.toEpochDay());
        book.competitors().add(c);
        return c;
    }

    private static void announce(MinecraftServer server, Component text) {
        server.getPlayerList().broadcastSystemMessage(Component.translatable("data_center_addon.market.news.prefix")
                .withStyle(ChatFormatting.AQUA).append(text), false);
    }

    /** "Shared Web, VPS" */
    static MutableComponent products(int mask) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (HostingProduct p : HostingProduct.values()) {
            if ((mask & (1 << p.ordinal())) != 0) {
                if (!first) {
                    out.append(", ");
                }
                out.append(Component.translatable(p.translationKey()));
                first = false;
            }
        }
        return out;
    }

    private static String percent(double share) {
        return Math.round(share * 100) + " %";
    }
}
