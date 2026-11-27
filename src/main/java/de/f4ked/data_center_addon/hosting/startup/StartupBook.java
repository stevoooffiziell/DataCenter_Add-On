package de.f4ked.data_center_addon.hosting.startup;

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
 * founding loan, milestones, setup fees and prepayments. Stored with the overworld.
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
        /** Most customers per product so far (only customers above it pay a setup fee); NaN until the first check. */
        final double[] lastCustomers = new double[HostingProduct.values().length];
        boolean loanTaken;
        long loanCents;
        long loanOutstandingCents;
        long loanStartDay;
        /** Milestones reached ({@link Milestone#bit()}), and whether the first check has run. */
        long milestones;
        boolean milestonesSeeded;
        /** Prepayment: whether new customers are offered annual plans, and the plans running. */
        boolean prepayEnabled = true;
        /** New customers per product that will book a plan once a whole one has come together. */
        final double[] prepayPending = new double[HostingProduct.values().length];
        final List<Prepayment> prepayments = new ArrayList<>();
        /** Customers per product at the last day's settlement (to see how many left); NaN until the first. */
        final double[] prepayLastTotal = new double[HostingProduct.values().length];
        long prepayReceivedCents;
        /** Prepayment settled against the income but not yet taken from the wallet. */
        double prepayDebtCents;
        /** Epoch day of the last settled in-game day; -1 before the first. */
        long lastDay = -1;

        Account() {
            Arrays.fill(lastCustomers, Double.NaN);
            Arrays.fill(prepayLastTotal, Double.NaN);
        }
    }

    /**
     * Customers of one product that paid {@code days} of service in advance. Their value is settled
     * day by day against the income they bring; customers that leave early get the rest back.
     */
    public static final class Prepayment {
        final HostingProduct product;
        double customers;
        /** Value of the service still owed to them, in cents. */
        double remainingCents;
        int daysLeft;

        Prepayment(HostingProduct product, double customers, double remainingCents, int daysLeft) {
            this.product = product;
            this.customers = customers;
            this.remainingCents = remainingCents;
            this.daysLeft = daysLeft;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("product", product.id());
            tag.putDouble("customers", customers);
            tag.putDouble("remaining", remainingCents);
            tag.putInt("days", daysLeft);
            return tag;
        }

        static Prepayment load(CompoundTag tag) {
            HostingProduct product = HostingProduct.byId(tag.getString("product"));
            return product == null ? null
                    : new Prepayment(product, tag.getDouble("customers"), tag.getDouble("remaining"), tag.getInt("days"));
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
            t.putBoolean("prepayEnabled", a.prepayEnabled);
            ListTag pending = new ListTag();
            for (double p : a.prepayPending) {
                CompoundTag pt = new CompoundTag();
                pt.putDouble("n", p);
                pending.add(pt);
            }
            t.put("prepayPending", pending);
            ListTag prepayments = new ListTag();
            for (Prepayment p : a.prepayments) {
                prepayments.add(p.save());
            }
            t.put("prepayments", prepayments);
            t.put("prepayLastTotal", doubles(a.prepayLastTotal));
            t.putLong("prepayReceived", a.prepayReceivedCents);
            t.putDouble("prepayDebt", a.prepayDebtCents);
            t.putLong("lastDay", a.lastDay);
            list.add(t);
        }
        tag.put("accounts", list);
        return tag;
    }

    private static ListTag doubles(double[] values) {
        ListTag list = new ListTag();
        for (double v : values) {
            CompoundTag t = new CompoundTag();
            t.putDouble("n", v);
            list.add(t);
        }
        return list;
    }

    private static void readDoubles(ListTag list, double[] into) {
        for (int i = 0; i < list.size() && i < into.length; i++) {
            into[i] = list.getCompound(i).getDouble("n");
        }
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
            a.prepayEnabled = !t.contains("prepayEnabled") || t.getBoolean("prepayEnabled");
            ListTag pending = t.getList("prepayPending", Tag.TAG_COMPOUND);
            for (int i = 0; i < pending.size() && i < a.prepayPending.length; i++) {
                a.prepayPending[i] = pending.getCompound(i).getDouble("n");
            }
            for (Tag pt : t.getList("prepayments", Tag.TAG_COMPOUND)) {
                Prepayment p = Prepayment.load((CompoundTag) pt);
                if (p != null) {
                    a.prepayments.add(p);
                }
            }
            readDoubles(t.getList("prepayLastTotal", Tag.TAG_COMPOUND), a.prepayLastTotal);
            a.prepayReceivedCents = t.getLong("prepayReceived");
            a.prepayDebtCents = t.getDouble("prepayDebt");
            a.lastDay = t.contains("lastDay") ? t.getLong("lastDay") : -1;
            book.accounts.put(t.getUUID("account"), a);
        }
        return book;
    }
}
