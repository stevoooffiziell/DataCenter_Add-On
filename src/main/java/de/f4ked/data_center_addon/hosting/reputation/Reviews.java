package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.hosting.CustomerSegment;
import com.example.datacenter.hosting.HostingCustomers;
import com.example.datacenter.hosting.HostingProduct;
import com.example.datacenter.wallet.OwnerMessages;
import de.f4ked.data_center_addon.hosting.market.MarketBook;
import de.f4ked.data_center_addon.hosting.market.ServiceQuality;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.UUID;

/**
 * Customer reviews: now and then (on about one game day in three) a customer writes a review, positive or
 * negative, with a message. The customer is picked by product and segment as the account's customers
 * are spread; the chance of a good review follows the quality of that product's service
 * ({@link ServiceQuality}): about 90 % with perfect service, down to 10 % with bad.
 */
public final class Reviews {
    static final double CHANCE_PER_DAY = 0.35;
    static final double RENOWN = 3;
    /** Message texts per kind: {@code data_center_addon.reputation.review.good.<n>} / {@code .bad.<n>}. */
    static final int TEXTS = 4;

    private Reviews() {
    }

    /** Maybe one review for the account on a new game day. */
    static void day(ServerLevel level, UUID id, ReputationBook.Account a, long today) {
        RandomSource random = level.getRandom();
        if (random.nextDouble() >= CHANCE_PER_DAY) {
            return;
        }
        HostingCustomers customers = HostingCustomers.get(level);
        HostingProduct[] products = HostingProduct.values();
        double total = 0;
        for (HostingProduct p : products) {
            total += customers.total(id, p);
        }
        if (total < 1) {
            return;
        }
        HostingProduct product = products[0];
        double pick = random.nextDouble() * total;
        for (HostingProduct p : products) {
            pick -= customers.total(id, p);
            if (pick <= 0) {
                product = p;
                break;
            }
        }
        CustomerSegment segment = pickSegment(customers.segments(id, product), random);
        double bad = ServiceQuality.badness(MarketBook.get(level.getServer()).entry(id, product));
        double good = Double.isNaN(bad) ? 0.75 : Math.max(0.1, Math.min(0.9, 0.9 - 1.6 * bad));
        boolean positive = random.nextDouble() < good;
        int text = random.nextInt(TEXTS);
        Renown.add(level, id, positive ? RENOWN : -RENOWN, Renown.Source.REVIEWS);
        a.lastReview = new long[]{today, positive ? 1 : 0, product.ordinal(), segment.ordinal(), text};
        OwnerMessages.toOwner(level.getServer(), id, message(positive, product, segment, text));
    }

    private static CustomerSegment pickSegment(double[] counts, RandomSource random) {
        CustomerSegment[] all = CustomerSegment.values();
        double sum = 0;
        for (double c : counts) {
            sum += c;
        }
        double pick = random.nextDouble() * sum;
        for (int s = 0; s < counts.length && s < all.length; s++) {
            pick -= counts[s];
            if (pick <= 0) {
                return all[s];
            }
        }
        return all[0];
    }

    /** "★★★★★ Review by an online shop (Shared Web): "…" +3 renown" */
    public static Component message(boolean positive, HostingProduct product, CustomerSegment segment, int text) {
        String kind = positive ? "good" : "bad";
        return Component.translatable("data_center_addon.reputation.review", positive ? "★★★★★" : "★☆☆☆☆",
                        Component.translatable(segment.translationKey()), Component.translatable(product.translationKey()),
                        Component.translatable("data_center_addon.reputation.review." + kind + "." + text),
                        (positive ? "+" : "−") + Math.round(RENOWN))
                .withStyle(positive ? ChatFormatting.GREEN : ChatFormatting.GOLD);
    }
}
