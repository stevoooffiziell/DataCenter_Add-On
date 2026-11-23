package de.f4ked.data_center_addon.client;

import com.example.datacenter.catalog.PartPacks;
import com.example.datacenter.component.FanSpec;
import com.example.datacenter.component.MainboardSpec;
import com.example.datacenter.component.ProcessorKind;
import com.example.datacenter.component.ProcessorSpec;
import com.example.datacenter.component.PsuSpec;
import com.example.datacenter.component.RamSpec;
import com.example.datacenter.component.StorageSpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rarity-style tier of a computer part, so better parts are easy to spot.
 * <p>
 * A part's tier is where it ranks among every known part of the same kind (Data Center's own parts
 * plus all data-pack parts): the weakest fifth or so is Common, the top few percent Legendary. Parts
 * are only compared with parts that are measured the same way, e.g. CPUs by compute, mining cards by
 * Ethash rate, PSUs by usable output.
 */
public enum PartTier {
    COMMON(0xFFC6C6C6, 0xFF8A8A8A),
    UNCOMMON(0xFF62D96B, 0xFF2E9E3E),
    RARE(0xFF4FA3FF, 0xFF2563C9),
    EPIC(0xFFBF73FF, 0xFF8436C9),
    LEGENDARY(0xFFFFAE3D, 0xFFD9761A);

    /** Upper percentile bounds of COMMON .. EPIC; everything above is LEGENDARY. */
    private static final double[] BOUNDS = {0.30, 0.55, 0.78, 0.93};

    /** Readable text colour. */
    public final int text;
    /** Darker shade for borders. */
    public final int border;

    PartTier(int text, int border) {
        this.text = text;
        this.border = border;
    }

    public Component displayName() {
        return Component.translatable("data_center_addon.tier." + name().toLowerCase(Locale.ROOT));
    }

    static PartTier ofPercentile(double p) {
        for (int i = 0; i < BOUNDS.length; i++) {
            if (p < BOUNDS[i]) {
                return values()[i];
            }
        }
        return LEGENDARY;
    }

    static PartTier ofOrdinal(int ordinal) {
        return values()[Math.max(0, Math.min(ordinal, values().length - 1))];
    }

    // ------------------------------------------------------------------ ranking

    /** A part's comparison group and its score inside that group (higher = better). */
    private record Rating(String group, double score) {
    }

    private static long cachedGeneration = Long.MIN_VALUE;
    /** Sorted, distinct scores per group. */
    private static Map<String, double[]> population = Map.of();

    /** The tier of a part stack, or {@code null} if it is not a rated part. */
    @Nullable
    public static PartTier of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Rating rating = rate(PartPacks.spec(stack));
        if (rating == null) {
            return null;
        }
        double[] scores = population().get(rating.group);
        if (scores == null || scores.length < 2) {
            return null;
        }
        int index = Arrays.binarySearch(scores, rating.score);
        double rank = index >= 0 ? index : -index - 1.5;
        return ofPercentile(Math.max(0, rank) / (scores.length - 1));
    }

    @Nullable
    private static Rating rate(@Nullable Object spec) {
        if (spec instanceof ProcessorSpec p) {
            String kind = p.getKind() == ProcessorKind.CPU ? "cpu" : "gpu";
            if (p.getComputePerTick() > 0) {
                return new Rating(kind + ":compute", p.getComputePerTick());
            }
            if (p.getEthashHashesPerSecond() > 0) {
                return new Rating(kind + ":ethash", p.getEthashHashesPerSecond());
            }
            if (p.getHashesPerSecond() > 0) {
                return new Rating(kind + ":sha256", p.getHashesPerSecond());
            }
            if (p.getKHeavyHashHashesPerSecond() > 0) {
                return new Rating(kind + ":kheavyhash", p.getKHeavyHashHashesPerSecond());
            }
            return null;
        }
        if (spec instanceof MainboardSpec m) {
            return new Rating("mainboard", m.getCpuSlots() * 8.0 + m.getGpuSlots() * 4.0
                    + m.getPsuSlots() * 2.0 + m.getRamSlots() + m.getStorageSlots() + m.getFanSlots());
        }
        if (spec instanceof RamSpec r) {
            // size first; channel bonus and stability only break ties
            return new Rating("ram", r.getSizeMb() + (r.getComputeMultiplier() + r.getCrashChanceReduction()) * 1e-3);
        }
        if (spec instanceof StorageSpec s) {
            return new Rating("storage", s.getCapacityGb() + (s.isSolidState() ? 0.5 : 0));
        }
        if (spec instanceof PsuSpec s) {
            return new Rating("psu", s.getMaxPowerThroughput() * (double) s.getEfficiency());
        }
        if (spec instanceof FanSpec f) {
            return new Rating("fan", f.getAirflowCfm());
        }
        return null;
    }

    /** Rebuilt whenever Data Center (re-)applies the part packs, e.g. when joining a world. */
    private static Map<String, double[]> population() {
        long generation = PartPacks.generation();
        if (generation == cachedGeneration) {
            return population;
        }
        Map<String, List<Double>> scores = new HashMap<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
            if ("datacenter".equals(entry.getKey().location().getNamespace())) {
                collect(scores, PartPacks.spec(new ItemStack(entry.getValue())));
            }
        }
        for (ResourceLocation id : PartPacks.packPartIds()) {
            collect(scores, PartPacks.packSpec(id));
        }
        Map<String, double[]> sorted = new HashMap<>();
        scores.forEach((group, list) -> sorted.put(group,
                list.stream().mapToDouble(Double::doubleValue).distinct().sorted().toArray()));
        population = sorted;
        cachedGeneration = generation;
        return population;
    }

    private static void collect(Map<String, List<Double>> scores, @Nullable Object spec) {
        Rating rating = rate(spec);
        if (rating != null) {
            scores.computeIfAbsent(rating.group, g -> new ArrayList<>()).add(rating.score);
        }
    }
}
