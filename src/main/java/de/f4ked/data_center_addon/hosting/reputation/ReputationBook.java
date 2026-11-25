package de.f4ked.data_center_addon.hosting.reputation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The add-on's reputation of each account ({@link ReputationService}): the service of the last
 * {@link Ruf#WINDOW_DAYS} in-game days, from which the Ruf is computed. Stored with the overworld.
 */
public final class ReputationBook extends SavedData {
    private static final String NAME = "data_center_addon_reputation";
    static final UUID NOBODY = new UUID(0L, 0L);

    private final Map<UUID, Account> accounts = new HashMap<>();

    /** One account. Day records hold {@link Ruf#SERVED}, {@link Ruf#DOWN}, ... */
    public static final class Account {
        /** The day being recorded. */
        final double[] today = new double[Ruf.FIELDS];
        /** The finished days, oldest first, at most {@link Ruf#WINDOW_DAYS}. */
        final Deque<double[]> days = new ArrayDeque<>();
        /** Epoch day of {@link #today}; -1 before the first record. */
        long day = -1;
        /** Renown booked by source since the add-on's reputation model runs ({@link Renown}). */
        final EnumMap<Renown.Source, Double> sources = new EnumMap<>(Renown.Source.class);
        /** Most customers the account ever had; NaN until first seen. */
        double customerMark = Double.NaN;
        /** The last customer review: {day, positive (1/0), product, segment, text}; null before the first. */
        @Nullable
        long[] lastReview;
        /** Highest room certification level a serving rack of the account had; -1 until first seen. */
        int maxTier = -1;
        /** New customers in their first month: {start day, count, disrupted (1) or not (0)}, oldest first. */
        final Deque<double[]> cohorts = new ArrayDeque<>();

        public EnumMap<Renown.Source, Double> sources() {
            return sources;
        }
    }

    public static ReputationBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ReputationBook::new, ReputationBook::load), NAME);
    }

    static UUID key(@Nullable UUID owner) {
        return owner == null ? NOBODY : owner;
    }

    public Account account(@Nullable UUID owner) {
        return accounts.computeIfAbsent(key(owner), k -> new Account());
    }

    Map<UUID, Account> accounts() {
        return accounts;
    }

    // ------------------------------------------------------------------ saving

    private static ReputationBook load(CompoundTag tag, HolderLookup.Provider registries) {
        ReputationBook book = new ReputationBook();
        for (Tag t : tag.getList("accounts", Tag.TAG_COMPOUND)) {
            CompoundTag a = (CompoundTag) t;
            if (!a.hasUUID("id")) {
                continue;
            }
            Account account = book.account(a.getUUID("id"));
            account.day = a.getLong("day");
            readRecord(a.getList("today", Tag.TAG_DOUBLE), account.today);
            for (Tag d : a.getList("days", Tag.TAG_LIST)) {
                double[] record = new double[Ruf.FIELDS];
                readRecord((ListTag) d, record);
                account.days.addLast(record);
            }
            while (account.days.size() > Ruf.WINDOW_DAYS) {
                account.days.removeFirst();
            }
            CompoundTag sources = a.getCompound("sources");
            for (Renown.Source s : Renown.Source.values()) {
                if (sources.contains(s.name())) {
                    account.sources.put(s, sources.getDouble(s.name()));
                }
            }
            account.customerMark = a.contains("customerMark") ? a.getDouble("customerMark") : Double.NaN;
            account.maxTier = a.contains("maxTier") ? a.getInt("maxTier") : -1;
            long[] review = a.getLongArray("lastReview");
            account.lastReview = review.length == 5 ? review : null;
            for (Tag c : a.getList("cohorts", Tag.TAG_LIST)) {
                double[] cohort = new double[3];
                readRecord((ListTag) c, cohort);
                account.cohorts.addLast(cohort);
            }
        }
        return book;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        accounts.forEach((id, account) -> {
            CompoundTag a = new CompoundTag();
            a.putUUID("id", id);
            a.putLong("day", account.day);
            a.put("today", writeRecord(account.today));
            ListTag days = new ListTag();
            for (double[] record : account.days) {
                days.add(writeRecord(record));
            }
            a.put("days", days);
            CompoundTag sources = new CompoundTag();
            account.sources.forEach((s, v) -> sources.putDouble(s.name(), v));
            a.put("sources", sources);
            if (!Double.isNaN(account.customerMark)) {
                a.putDouble("customerMark", account.customerMark);
            }
            a.putInt("maxTier", account.maxTier);
            if (account.lastReview != null) {
                a.putLongArray("lastReview", account.lastReview);
            }
            ListTag cohorts = new ListTag();
            for (double[] cohort : account.cohorts) {
                cohorts.add(writeRecord(cohort));
            }
            a.put("cohorts", cohorts);
            list.add(a);
        });
        tag.put("accounts", list);
        return tag;
    }

    private static ListTag writeRecord(double[] record) {
        ListTag list = new ListTag();
        for (double v : record) {
            list.add(net.minecraft.nbt.DoubleTag.valueOf(v));
        }
        return list;
    }

    private static void readRecord(ListTag list, double[] into) {
        for (int i = 0; i < into.length && i < list.size(); i++) {
            double v = list.getDouble(i);
            into[i] = Double.isFinite(v) ? Math.max(0, v) : 0;
        }
    }
}
