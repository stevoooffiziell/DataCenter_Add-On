package de.f4ked.data_center_addon.hosting.market;

import com.example.datacenter.hosting.HostingProduct;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the add-on keeps about each account's markets ({@link MarketModel}): per account and product how
 * well known the account is and how well it serves its customers. Stored with the overworld.
 */
public final class MarketBook extends SavedData {
    private static final String NAME = "data_center_addon_market";
    /** Data Center's key for "no owner". */
    static final UUID NOBODY = new UUID(0L, 0L);

    private final Map<UUID, EnumMap<HostingProduct, Entry>> entries = new HashMap<>();

    /** One account's market of one product. */
    public static final class Entry {
        /** How well known the account is for the product, 0..1; NaN until first seen. */
        double awareness = Double.NaN;
        /** New customers waiting for a free place. */
        double waiting;
        /** Price in steps of {@link MarketModel#PRICE_STEP} from the list price ({@link MarketModel#PRICE_MIN}..{@link MarketModel#PRICE_MAX}). */
        int priceStep;
        /** The last step's new customers per month: closing the gap, and by word of mouth (not saved). */
        double ordersGap, ordersWord;
        /** Averages of the service ({@link ServiceQuality}): downtime, overload, throttling, 0..1; NaN before the first report. */
        double down = Double.NaN, overload = Double.NaN, throttle = Double.NaN;

        public double awareness() {
            return Double.isNaN(awareness) ? 0 : awareness;
        }

        public double waiting() {
            return waiting;
        }

        public int priceStep() {
            return priceStep;
        }
    }

    public static MarketBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MarketBook::new, MarketBook::load), NAME);
    }

    static UUID key(@Nullable UUID owner) {
        return owner == null ? NOBODY : owner;
    }

    public Entry entry(@Nullable UUID owner, HostingProduct product) {
        return entries.computeIfAbsent(key(owner), k -> new EnumMap<>(HostingProduct.class))
                .computeIfAbsent(product, p -> new Entry());
    }

    Map<UUID, EnumMap<HostingProduct, Entry>> entries() {
        return entries;
    }

    // ------------------------------------------------------------------ saving

    private static MarketBook load(CompoundTag tag, HolderLookup.Provider registries) {
        MarketBook book = new MarketBook();
        for (Tag t : tag.getList("owners", Tag.TAG_COMPOUND)) {
            CompoundTag o = (CompoundTag) t;
            if (!o.hasUUID("owner")) {
                continue;
            }
            UUID owner = o.getUUID("owner");
            CompoundTag products = o.getCompound("products");
            for (String id : products.getAllKeys()) {
                HostingProduct product = HostingProduct.byId(id);
                if (product == null) {
                    continue;
                }
                CompoundTag p = products.getCompound(id);
                Entry e = book.entry(owner, product);
                if (p.contains("awareness")) {
                    e.awareness = clamp01(p.getDouble("awareness"));
                }
                e.priceStep = Math.max(MarketModel.PRICE_MIN, Math.min(MarketModel.PRICE_MAX, p.getInt("price")));
                double waiting = p.getDouble("waiting");
                e.waiting = Double.isFinite(waiting) ? Math.max(0, waiting) : 0;
                if (p.contains("down")) {
                    e.down = clamp01(p.getDouble("down"));
                    e.overload = clamp01(p.getDouble("overload"));
                    e.throttle = clamp01(p.getDouble("throttle"));
                }
            }
        }
        return book;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        entries.forEach((owner, byProduct) -> {
            CompoundTag products = new CompoundTag();
            byProduct.forEach((product, e) -> {
                CompoundTag p = new CompoundTag();
                if (!Double.isNaN(e.awareness)) {
                    p.putDouble("awareness", e.awareness);
                }
                if (e.waiting > 0) {
                    p.putDouble("waiting", e.waiting);
                }
                if (e.priceStep != 0) {
                    p.putInt("price", e.priceStep);
                }
                if (!Double.isNaN(e.down)) {
                    p.putDouble("down", e.down);
                    p.putDouble("overload", e.overload);
                    p.putDouble("throttle", e.throttle);
                }
                products.put(product.id(), p);
            });
            CompoundTag o = new CompoundTag();
            o.putUUID("owner", owner);
            o.put("products", products);
            owners.add(o);
        });
        tag.put("owners", owners);
        return tag;
    }

    static double clamp01(double v) {
        return Double.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0;
    }
}
