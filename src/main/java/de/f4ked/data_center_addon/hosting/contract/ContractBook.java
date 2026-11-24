package de.f4ked.data_center_addon.hosting.contract;

import com.example.datacenter.hosting.HostingProduct;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hosting contracts per account (the same account Data Center keeps wallets and reputation for): the
 * offers waiting to be taken and the contracts running. Stored with the overworld.
 */
public final class ContractBook extends SavedData {
    private static final String NAME = "data_center_addon_contracts";

    private final Map<UUID, Account> accounts = new HashMap<>();

    /** One account's offers and running contracts. */
    public static final class Account {
        final List<Contract> offers = new ArrayList<>();
        final List<Contract> active = new ArrayList<>();
        int nextId = 1;
        /** Epoch day of the last in-game day that was settled; -1 before the first. */
        long lastDay = -1;
        /** Delivery orders: offers, running ones, and whether the first offers were made. */
        final List<Delivery> deliveryOffers = new ArrayList<>();
        final List<Delivery> deliveries = new ArrayList<>();
        boolean deliveriesSeeded;

        public List<Contract> offers() {
            return offers;
        }

        public List<Contract> active() {
            return active;
        }
    }

    /**
     * An offer or a running contract: a client wants {@code accounts} places of {@code product} kept
     * free for it for {@code days} in-game days, and pays for every day they are.
     */
    public static final class Contract {
        final int id;
        final int client;
        final HostingProduct product;
        final int accounts;
        final int days;
        final long centsPerDay;
        final long bonusCents;
        final long penaltyCents;
        final int reputation;
        /** Offer: last epoch day it can be taken. */
        long expiresDay;
        /** Running: days paid so far, days missed, and today's samples. */
        int daysDone;
        int strikes;
        int samples;
        int samplesMet;
        /** Whether the last measured day was met; days skipped by sleeping end the same way. Not saved. */
        boolean lastMet = true;
        /** Emergency order: short, well paid, open for one day only. */
        boolean urgent;
        /** Small hobbyist order: a few places, no penalty, no reputation lost when it fails. */
        boolean mini;

        Contract(int id, int client, HostingProduct product, int accounts, int days, long centsPerDay, long bonusCents,
                 long penaltyCents, int reputation, long expiresDay) {
            this.id = id;
            this.client = client;
            this.product = product;
            this.accounts = accounts;
            this.days = days;
            this.centsPerDay = centsPerDay;
            this.bonusCents = bonusCents;
            this.penaltyCents = penaltyCents;
            this.reputation = reputation;
            this.expiresDay = expiresDay;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", id);
            tag.putInt("client", client);
            tag.putString("product", product.id());
            tag.putInt("accounts", accounts);
            tag.putInt("days", days);
            tag.putLong("perDay", centsPerDay);
            tag.putLong("bonus", bonusCents);
            tag.putLong("penalty", penaltyCents);
            tag.putInt("reputation", reputation);
            tag.putLong("expires", expiresDay);
            tag.putInt("done", daysDone);
            tag.putInt("strikes", strikes);
            tag.putInt("samples", samples);
            tag.putInt("met", samplesMet);
            tag.putBoolean("urgent", urgent);
            tag.putBoolean("mini", mini);
            return tag;
        }

        /** Reputation a failed or cancelled contract costs. */
        int reputationLoss() {
            return mini ? 0 : reputation * 2;
        }

        static Contract load(CompoundTag tag) {
            HostingProduct product = HostingProduct.byId(tag.getString("product"));
            if (product == null) {
                return null;
            }
            Contract c = new Contract(tag.getInt("id"), tag.getInt("client"), product, tag.getInt("accounts"), tag.getInt("days"),
                    tag.getLong("perDay"), tag.getLong("bonus"), tag.getLong("penalty"), tag.getInt("reputation"), tag.getLong("expires"));
            c.daysDone = tag.getInt("done");
            c.strikes = tag.getInt("strikes");
            c.samples = tag.getInt("samples");
            c.samplesMet = tag.getInt("met");
            c.urgent = tag.getBoolean("urgent");
            c.mini = tag.getBoolean("mini");
            return c;
        }
    }

    /** One thing a delivery order asks for: {@code count} items matching {@code kind} (and {@code item} for ITEM). */
    public static final class Goal {
        final DeliveryService.GoalKind kind;
        /** ITEM: the item wanted; other kinds: the item shown as icon. */
        final ResourceLocation item;
        final int count;
        int delivered;

        Goal(DeliveryService.GoalKind kind, ResourceLocation item, int count) {
            this.kind = kind;
            this.item = item;
            this.count = count;
        }

        boolean done() {
            return delivered >= count;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("kind", kind.name());
            tag.putString("item", item.toString());
            tag.putInt("count", count);
            tag.putInt("delivered", delivered);
            return tag;
        }

        static Goal load(CompoundTag tag) {
            DeliveryService.GoalKind kind;
            try {
                kind = DeliveryService.GoalKind.valueOf(tag.getString("kind"));
            } catch (IllegalArgumentException e) {
                return null;
            }
            ResourceLocation item = ResourceLocation.tryParse(tag.getString("item"));
            if (item == null) {
                return null;
            }
            Goal goal = new Goal(kind, item, Math.max(1, tag.getInt("count")));
            goal.delivered = tag.getInt("delivered");
            return goal;
        }
    }

    /**
     * A delivery order (offer or running): a client wants the goods of its {@link Goal}s delivered to a
     * Hosting Manager before {@code dueDay} and pays {@code payCents} and reputation when all are in.
     */
    public static final class Delivery {
        final int id;
        final int client;
        final List<Goal> goals;
        final long payCents;
        final int reputation;
        /** In-game days the client gives from accepting. */
        final int days;
        /** Offer: last epoch day it can be taken. Running: last epoch day to deliver. */
        long lastDay;

        Delivery(int id, int client, List<Goal> goals, long payCents, int reputation, int days, long lastDay) {
            this.id = id;
            this.client = client;
            this.goals = goals;
            this.payCents = payCents;
            this.reputation = reputation;
            this.days = days;
            this.lastDay = lastDay;
        }

        boolean complete() {
            for (Goal goal : goals) {
                if (!goal.done()) {
                    return false;
                }
            }
            return true;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", id);
            tag.putInt("client", client);
            ListTag list = new ListTag();
            for (Goal goal : goals) {
                list.add(goal.save());
            }
            tag.put("goals", list);
            tag.putLong("pay", payCents);
            tag.putInt("reputation", reputation);
            tag.putInt("days", days);
            tag.putLong("lastDay", lastDay);
            return tag;
        }

        static Delivery load(CompoundTag tag) {
            List<Goal> goals = new ArrayList<>();
            for (Tag t : tag.getList("goals", Tag.TAG_COMPOUND)) {
                Goal goal = Goal.load((CompoundTag) t);
                if (goal != null) {
                    goals.add(goal);
                }
            }
            if (goals.isEmpty()) {
                return null;
            }
            return new Delivery(tag.getInt("id"), tag.getInt("client"), goals, tag.getLong("pay"), tag.getInt("reputation"),
                    tag.getInt("days"), tag.getLong("lastDay"));
        }
    }

    public static ContractBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ContractBook::new, ContractBook::load, null), NAME);
    }

    public Account account(UUID account) {
        return accounts.computeIfAbsent(account, a -> new Account());
    }

    Map<UUID, Account> accounts() {
        return accounts;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Account> entry : accounts.entrySet()) {
            Account account = entry.getValue();
            CompoundTag a = new CompoundTag();
            a.putUUID("account", entry.getKey());
            a.putInt("nextId", account.nextId);
            a.putLong("lastDay", account.lastDay);
            a.put("offers", saveAll(account.offers));
            a.put("active", saveAll(account.active));
            a.put("deliveryOffers", saveDeliveries(account.deliveryOffers));
            a.put("deliveries", saveDeliveries(account.deliveries));
            a.putBoolean("deliveriesSeeded", account.deliveriesSeeded);
            list.add(a);
        }
        tag.put("accounts", list);
        return tag;
    }

    private static ListTag saveAll(List<Contract> contracts) {
        ListTag list = new ListTag();
        for (Contract c : contracts) {
            list.add(c.save());
        }
        return list;
    }

    static ContractBook load(CompoundTag tag, HolderLookup.Provider registries) {
        ContractBook book = new ContractBook();
        for (Tag t : tag.getList("accounts", Tag.TAG_COMPOUND)) {
            CompoundTag a = (CompoundTag) t;
            Account account = new Account();
            account.nextId = Math.max(1, a.getInt("nextId"));
            account.lastDay = a.contains("lastDay") ? a.getLong("lastDay") : -1;
            loadAll(a.getList("offers", Tag.TAG_COMPOUND), account.offers);
            loadAll(a.getList("active", Tag.TAG_COMPOUND), account.active);
            loadDeliveries(a.getList("deliveryOffers", Tag.TAG_COMPOUND), account.deliveryOffers);
            loadDeliveries(a.getList("deliveries", Tag.TAG_COMPOUND), account.deliveries);
            account.deliveriesSeeded = a.getBoolean("deliveriesSeeded");
            book.accounts.put(a.getUUID("account"), account);
        }
        return book;
    }

    private static ListTag saveDeliveries(List<Delivery> deliveries) {
        ListTag list = new ListTag();
        for (Delivery d : deliveries) {
            list.add(d.save());
        }
        return list;
    }

    private static void loadDeliveries(ListTag list, List<Delivery> into) {
        for (Tag t : list) {
            Delivery d = Delivery.load((CompoundTag) t);
            if (d != null) {
                into.add(d);
            }
        }
    }

    private static void loadAll(ListTag list, List<Contract> into) {
        for (Tag t : list) {
            Contract c = Contract.load((CompoundTag) t);
            if (c != null) {
                into.add(c);
            }
        }
    }
}
