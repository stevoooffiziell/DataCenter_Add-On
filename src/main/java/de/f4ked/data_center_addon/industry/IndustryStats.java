package de.f4ked.data_center_addon.industry;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What each company's factory bought and sold, per in-game day for the last {@link #DAYS} days: units and
 * cents per raw material ({@link Commodity}) and per product ({@link Product}). The Factory Manager shows it.
 */
public final class IndustryStats extends SavedData {
    private static final String NAME = "data_center_addon_industry_stats";
    public static final int DAYS = 30;
    private static final int C = Commodity.values().length, P = Product.values().length;
    /** Layout of a day record: day, then per commodity units and cents, then per product units and cents. */
    private static final int BOUGHT = 1, SOLD = BOUGHT + 2 * C, SIZE = SOLD + 2 * P;

    private final Map<UUID, Deque<long[]>> accounts = new HashMap<>();

    public static IndustryStats get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(IndustryStats::new, IndustryStats::load), NAME);
    }

    private long[] day(UUID account, long today) {
        Deque<long[]> days = accounts.computeIfAbsent(account, a -> new ArrayDeque<>());
        long[] last = days.peekLast();
        if (last == null || last[0] != today) {
            last = new long[SIZE];
            last[0] = today;
            days.addLast(last);
        }
        while (!days.isEmpty() && today - days.peekFirst()[0] >= DAYS) {
            days.removeFirst();
        }
        return last;
    }

    public void bought(UUID account, Commodity c, int units, long cents, long today) {
        long[] d = day(account, today);
        d[BOUGHT + 2 * c.ordinal()] += units;
        d[BOUGHT + 2 * c.ordinal() + 1] += cents;
        setDirty();
    }

    public void sold(UUID account, Product p, int units, long cents, long today) {
        long[] d = day(account, today);
        d[SOLD + 2 * p.ordinal()] += units;
        d[SOLD + 2 * p.ordinal() + 1] += cents;
        setDirty();
    }

    /** Sums of the last {@code days} days (1 = today): {units, cents} per commodity and per product. */
    public Totals totals(UUID account, long today, int days) {
        long[] sum = new long[SIZE];
        for (long[] d : accounts.getOrDefault(account, new ArrayDeque<>())) {
            if (today - d[0] < days) {
                for (int i = 1; i < SIZE; i++) {
                    sum[i] += d[i];
                }
            }
        }
        return new Totals(sum);
    }

    /** Totals over some days. */
    public record Totals(long[] sum) {
        public long boughtUnits(Commodity c) {
            return sum[BOUGHT + 2 * c.ordinal()];
        }

        public long boughtCents(Commodity c) {
            return sum[BOUGHT + 2 * c.ordinal() + 1];
        }

        public long soldUnits(Product p) {
            return sum[SOLD + 2 * p.ordinal()];
        }

        public long soldCents(Product p) {
            return sum[SOLD + 2 * p.ordinal() + 1];
        }

        public long spent() {
            long s = 0;
            for (Commodity c : Commodity.values()) {
                s += boughtCents(c);
            }
            return s;
        }

        public long revenue() {
            long s = 0;
            for (Product p : Product.values()) {
                s += soldCents(p);
            }
            return s;
        }
    }

    private static IndustryStats load(CompoundTag tag, HolderLookup.Provider registries) {
        IndustryStats stats = new IndustryStats();
        for (Tag t : tag.getList("accounts", Tag.TAG_COMPOUND)) {
            CompoundTag a = (CompoundTag) t;
            if (!a.hasUUID("id")) {
                continue;
            }
            Deque<long[]> days = new ArrayDeque<>();
            for (Tag d : a.getList("days", Tag.TAG_LONG_ARRAY)) {
                long[] saved = ((LongArrayTag) d).getAsLongArray();
                long[] day = new long[SIZE];
                System.arraycopy(saved, 0, day, 0, Math.min(saved.length, SIZE));
                days.addLast(day);
            }
            stats.accounts.put(a.getUUID("id"), days);
        }
        return stats;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        accounts.forEach((id, days) -> {
            CompoundTag a = new CompoundTag();
            a.putUUID("id", id);
            ListTag dl = new ListTag();
            for (long[] d : days) {
                dl.add(new LongArrayTag(d));
            }
            a.put("days", dl);
            list.add(a);
        });
        tag.put("accounts", list);
        return tag;
    }
}
