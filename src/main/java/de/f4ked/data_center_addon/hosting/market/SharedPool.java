package de.f4ked.data_center_addon.hosting.market;

import com.example.datacenter.hosting.HostingProduct;
import de.f4ked.data_center_addon.Config;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Several players in one market ({@link Config#sharedPool()}, off by default). Without it every account
 * has the whole pool to itself. With it the accounts that sell a product share its pool and compete
 * through what makes their share: reputation, customer classes, awareness and price.
 * <p>
 * An account with the standalone share {@code s} keeps {@code 2s / (s + S)} of its potential, where
 * {@code S} is the summed share of all accounts active in the product (itself included):
 * <ul>
 *   <li>alone: ×1, nothing changes;</li>
 *   <li>two equally strong accounts: ×0.67 each;</li>
 *   <li>one twice as strong as the other: ×0.80 for the stronger, ×0.50 for the weaker.</li>
 * </ul>
 * An account counts as active while Data Center moves its customers of the product on (it offers places
 * or has customers).
 */
public final class SharedPool {
    /** Ticks after its last step an account still counts as active in a product. */
    static final long ACTIVE_TICKS = 200;

    private SharedPool() {
    }

    /** What the sharing leaves of an account's potential, and with how many accounts it shares. */
    public record Sharing(double factor, int accounts) {
        static final Sharing ALONE = new Sharing(1.0, 1);
    }

    /** Notes the account's standalone share of the product (called by every step). */
    static void note(MarketBook.Entry entry, double share, long tick) {
        entry.share = share;
        entry.lastSeen = tick;
    }

    /** The sharing of an account's potential; ALONE while the option is off. */
    static Sharing sharing(MarketBook book, HostingProduct product, MarketBook.Entry self, double ownShare, long tick) {
        if (!Config.sharedPool() || ownShare <= 0) {
            return Sharing.ALONE;
        }
        double sum = ownShare;
        int accounts = 1;
        for (Map.Entry<UUID, EnumMap<HostingProduct, MarketBook.Entry>> byOwner : book.entries().entrySet()) {
            MarketBook.Entry e = byOwner.getValue().get(product);
            if (e != null && e != self && e.share > 0 && tick - e.lastSeen <= ACTIVE_TICKS) {
                sum += e.share;
                accounts++;
            }
        }
        return new Sharing(2 * ownShare / (ownShare + sum), accounts);
    }
}
