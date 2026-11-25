package de.f4ked.data_center_addon.hosting.market;

import com.example.datacenter.hosting.CustomerSegment;
import com.example.datacenter.hosting.HostingGrowth;
import com.example.datacenter.hosting.HostingProduct;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Segment pools: some customer segments grow with what happens in the world. Game server customers come
 * with the big multiplayer games, streaming customers with the video platforms.
 * <p>
 * Every launch raises its segment's pool for good ({@code lasting}) and for a while more
 * ({@code hype}, fading by 63 % in {@link #HYPE_MONTHS} months). Data Center already lets game servers
 * start in June 1999 and streaming in February 2005; the launches here come on top.
 * <p>
 * A product's pool grows by the boosts of its segments, weighted by Data Center's segment mix of the
 * product, and its new customers come in the boosted mix.
 */
public final class SegmentPools {
    static final double HYPE_MONTHS = 12.0;

    /** A launch that raises one segment's pool. */
    public record Launch(CustomerSegment segment, LocalDate date, String name, double lasting, double hype) {
    }

    static final List<Launch> LAUNCHES = List.of(
            new Launch(CustomerSegment.GAME_SERVER, LocalDate.of(1999, 6, 19), "Counter-Strike (Beta)", 0.05, 0.15),
            new Launch(CustomerSegment.GAME_SERVER, LocalDate.of(2000, 11, 9), "Counter-Strike 1.0", 0.15, 0.25),
            new Launch(CustomerSegment.GAME_SERVER, LocalDate.of(2002, 9, 10), "Battlefield 1942", 0.05, 0.15),
            new Launch(CustomerSegment.GAME_SERVER, LocalDate.of(2004, 11, 1), "Counter-Strike: Source", 0.10, 0.20),
            new Launch(CustomerSegment.GAME_SERVER, LocalDate.of(2004, 11, 23), "World of Warcraft", 0.10, 0.30),
            new Launch(CustomerSegment.GAME_SERVER, LocalDate.of(2007, 10, 10), "Team Fortress 2", 0.05, 0.15),
            new Launch(CustomerSegment.GAME_SERVER, LocalDate.of(2007, 11, 5), "Call of Duty 4", 0.05, 0.15),
            new Launch(CustomerSegment.GAME_SERVER, LocalDate.of(2011, 11, 18), "Minecraft", 0.25, 0.40),
            new Launch(CustomerSegment.GAME_SERVER, LocalDate.of(2017, 8, 29), "ARK: Survival Evolved", 0.05, 0.15),
            new Launch(CustomerSegment.GAME_SERVER, LocalDate.of(2018, 2, 8), "Rust", 0.05, 0.15),
            new Launch(CustomerSegment.GAME_SERVER, LocalDate.of(2021, 2, 2), "Valheim", 0.05, 0.30),
            new Launch(CustomerSegment.GAME_SERVER, LocalDate.of(2024, 1, 19), "Palworld", 0.03, 0.25),
            new Launch(CustomerSegment.STREAMING, LocalDate.of(2005, 2, 14), "YouTube", 0.20, 0.30),
            new Launch(CustomerSegment.STREAMING, LocalDate.of(2007, 1, 16), "Netflix Streaming", 0.20, 0.20),
            new Launch(CustomerSegment.STREAMING, LocalDate.of(2011, 6, 6), "Twitch", 0.20, 0.30));

    private SegmentPools() {
    }

    /** How much the segment's pool is raised on that date (1 = not at all). */
    public static double boost(CustomerSegment segment, LocalDate date) {
        double boost = 1;
        for (Launch launch : LAUNCHES) {
            if (launch.segment == segment && !date.isBefore(launch.date)) {
                double months = ChronoUnit.DAYS.between(launch.date, date) / 30.44;
                boost += launch.lasting + launch.hype * Math.exp(-months / HYPE_MONTHS);
            }
        }
        return boost;
    }

    /** The latest launch of the segment up to that date, or null. */
    @Nullable
    public static Launch latest(CustomerSegment segment, LocalDate date) {
        Launch latest = null;
        for (Launch launch : LAUNCHES) {
            if (launch.segment == segment && !date.isBefore(launch.date)) {
                latest = launch;
            }
        }
        return latest;
    }

    /** Factor on the product's pool: the segment boosts weighted by Data Center's segment mix of the product. */
    public static double poolFactor(HostingProduct product, LocalDate date) {
        double[] mix = HostingGrowth.orderMix(product, date);
        CustomerSegment[] segments = CustomerSegment.values();
        double sum = 0, weight = 0;
        for (int s = 0; s < mix.length && s < segments.length; s++) {
            sum += mix[s] * boost(segments[s], date);
            weight += mix[s];
        }
        return weight <= 0 ? 1 : sum / weight;
    }

    /** Data Center's segment mix of the product, weighted by the segment boosts. */
    public static double[] mix(HostingProduct product, LocalDate date) {
        double[] mix = HostingGrowth.orderMix(product, date);
        CustomerSegment[] segments = CustomerSegment.values();
        double[] out = new double[mix.length];
        double total = 0;
        for (int s = 0; s < mix.length && s < segments.length; s++) {
            out[s] = mix[s] * boost(segments[s], date);
            total += out[s];
        }
        for (int s = 0; s < out.length; s++) {
            out[s] = total <= 0 ? mix[s] : out[s] / total;
        }
        return out;
    }

    /** One raised segment of a product, for the Market tab. */
    public record Boost(CustomerSegment segment, double factor, @Nullable Launch latest) {
    }

    /** The raised segments that make up a noticeable part of the product's customers. */
    public static List<Boost> boosts(HostingProduct product, LocalDate date) {
        double[] mix = HostingGrowth.orderMix(product, date);
        CustomerSegment[] segments = CustomerSegment.values();
        List<Boost> out = new ArrayList<>();
        for (int s = 0; s < mix.length && s < segments.length; s++) {
            double boost = boost(segments[s], date);
            if (mix[s] >= 0.02 && boost > 1.005) {
                out.add(new Boost(segments[s], boost, latest(segments[s], date)));
            }
        }
        return out;
    }
}
