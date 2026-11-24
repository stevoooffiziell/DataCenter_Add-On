package de.f4ked.data_center_addon.hosting;

import com.example.datacenter.hosting.HostingProduct;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The founding phase of each account's hosting company ({@link StartupService}): start bonus, grant,
 * founding loan, milestones, setup fees and service tickets. Stored with the overworld.
 */
public final class StartupBook extends SavedData {
    private static final String NAME = "data_center_addon_startup";

    private final Map<UUID, Account> accounts = new HashMap<>();

    public static final class Account {
        /** Epoch day of the first rack income; -1 before. */
        long startDay = -1;
        /** An established company when first seen: no start bonus and no grant. */
        boolean veteran;
        long grantCents;
        /** Rack income since the start, in cents (for the revenue-limited start bonus). */
        double revenueCents;
        /** Start bonus earned but not yet paid out (fractions of a cent). */
        double bonusOwedCents;
        long bonusPaidCents;
        /** Whether the end of the start bonus was announced. */
        boolean bonusEnded;
        /** Setup fees earned but not yet paid out, and paid so far. */
        double feeOwedCents;
        long feesPaidCents;
        /** Customers per product at the last check; NaN until the first. */
        final double[] lastCustomers = new double[HostingProduct.values().length];
        boolean loanTaken;
        long loanCents;
        long loanOutstandingCents;
        long loanStartDay;
        /** Milestones reached ({@link Milestone#bit()}), and whether the first check has run. */
        long milestones;
        boolean milestonesSeeded;
        final List<Ticket> tickets = new ArrayList<>();
        int nextTicketId = 1;
        /** Epoch day of the last settled in-game day; -1 before the first. */
        long lastDay = -1;

        Account() {
            Arrays.fill(lastCustomers, Double.NaN);
        }
    }

    /** A service ticket: a client wants someone to look at one of the account's racks before {@code lastDay}. */
    public static final class Ticket {
        final int id;
        final ResourceLocation dimension;
        final BlockPos pos;
        final int kind;
        final long rewardCents;
        final long lastDay;

        Ticket(int id, ResourceLocation dimension, BlockPos pos, int kind, long rewardCents, long lastDay) {
            this.id = id;
            this.dimension = dimension;
            this.pos = pos;
            this.kind = kind;
            this.rewardCents = rewardCents;
            this.lastDay = lastDay;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", id);
            tag.putString("dim", dimension.toString());
            tag.putLong("pos", pos.asLong());
            tag.putInt("kind", kind);
            tag.putLong("reward", rewardCents);
            tag.putLong("lastDay", lastDay);
            return tag;
        }

        static Ticket load(CompoundTag tag) {
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dim"));
            return dim == null ? null : new Ticket(tag.getInt("id"), dim, BlockPos.of(tag.getLong("pos")), tag.getInt("kind"),
                    tag.getLong("reward"), tag.getLong("lastDay"));
        }
    }

    public static StartupBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StartupBook::new, StartupBook::load, null), NAME);
    }

    public Account account(UUID account) {
        return accounts.computeIfAbsent(account, a -> {
            setDirty();
            return new Account();
        });
    }

    Map<UUID, Account> accounts() {
        return accounts;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Account> entry : accounts.entrySet()) {
            Account a = entry.getValue();
            CompoundTag t = new CompoundTag();
            t.putUUID("account", entry.getKey());
            t.putLong("startDay", a.startDay);
            t.putBoolean("veteran", a.veteran);
            t.putLong("grant", a.grantCents);
            t.putDouble("revenue", a.revenueCents);
            t.putDouble("bonusOwed", a.bonusOwedCents);
            t.putLong("bonusPaid", a.bonusPaidCents);
            t.putBoolean("bonusEnded", a.bonusEnded);
            t.putDouble("feeOwed", a.feeOwedCents);
            t.putLong("feesPaid", a.feesPaidCents);
            ListTag customers = new ListTag();
            for (double c : a.lastCustomers) {
                CompoundTag ct = new CompoundTag();
                ct.putDouble("n", c);
                customers.add(ct);
            }
            t.put("customers", customers);
            t.putBoolean("loanTaken", a.loanTaken);
            t.putLong("loan", a.loanCents);
            t.putLong("loanOutstanding", a.loanOutstandingCents);
            t.putLong("loanStart", a.loanStartDay);
            t.putLong("milestones", a.milestones);
            t.putBoolean("milestonesSeeded", a.milestonesSeeded);
            ListTag tickets = new ListTag();
            for (Ticket ticket : a.tickets) {
                tickets.add(ticket.save());
            }
            t.put("tickets", tickets);
            t.putInt("nextTicket", a.nextTicketId);
            t.putLong("lastDay", a.lastDay);
            list.add(t);
        }
        tag.put("accounts", list);
        return tag;
    }

    static StartupBook load(CompoundTag tag, HolderLookup.Provider registries) {
        StartupBook book = new StartupBook();
        for (Tag raw : tag.getList("accounts", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Account a = new Account();
            a.startDay = t.getLong("startDay");
            a.veteran = t.getBoolean("veteran");
            a.grantCents = t.getLong("grant");
            a.revenueCents = t.getDouble("revenue");
            a.bonusOwedCents = t.getDouble("bonusOwed");
            a.bonusPaidCents = t.getLong("bonusPaid");
            a.bonusEnded = t.getBoolean("bonusEnded");
            a.feeOwedCents = t.getDouble("feeOwed");
            a.feesPaidCents = t.getLong("feesPaid");
            ListTag customers = t.getList("customers", Tag.TAG_COMPOUND);
            for (int i = 0; i < customers.size() && i < a.lastCustomers.length; i++) {
                a.lastCustomers[i] = customers.getCompound(i).getDouble("n");
            }
            a.loanTaken = t.getBoolean("loanTaken");
            a.loanCents = t.getLong("loan");
            a.loanOutstandingCents = t.getLong("loanOutstanding");
            a.loanStartDay = t.getLong("loanStart");
            a.milestones = t.getLong("milestones");
            a.milestonesSeeded = t.getBoolean("milestonesSeeded");
            for (Tag rt : t.getList("tickets", Tag.TAG_COMPOUND)) {
                Ticket ticket = Ticket.load((CompoundTag) rt);
                if (ticket != null) {
                    a.tickets.add(ticket);
                }
            }
            a.nextTicketId = Math.max(1, t.getInt("nextTicket"));
            a.lastDay = t.contains("lastDay") ? t.getLong("lastDay") : -1;
            book.accounts.put(t.getUUID("account"), a);
        }
        return book;
    }
}
