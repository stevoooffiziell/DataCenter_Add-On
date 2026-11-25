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
