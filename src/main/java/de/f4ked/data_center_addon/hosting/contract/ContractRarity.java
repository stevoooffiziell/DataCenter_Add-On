package de.f4ked.data_center_addon.hosting.contract;

import de.f4ked.data_center_addon.hosting.reputation.CustomerTier;
import net.minecraft.util.RandomSource;

import java.util.Locale;

/**
 * Rarity of a hosting contract, coloured like the tiers of the computer parts (grey Common to orange
 * Legendary). A rarer contract wants somewhat more places and pays more per place, a bigger bonus and more
 * reputation, but costs a higher penalty when it fails.
 * <p>
 * The chances shift with the customer class the account has unlocked ({@link #roll}): a new company mostly
 * gets common contracts, a renowned one many rare to legendary ones.
 *
 * @param size       multiplier on the places a contract wants (still never more than are free)
 * @param pay        multiplier on the pay per place and day
 * @param reputation multiplier on the reputation a fulfilled contract brings (and a failed one costs)
 * @param penalty    multiplier on the penalty of a failed or cancelled contract
 */
public enum ContractRarity {
    COMMON(1.00, 1.00, 1.0, 1.00),
    UNCOMMON(1.15, 1.25, 1.5, 1.10),
    RARE(1.30, 1.60, 2.0, 1.25),
    EPIC(1.50, 2.20, 3.0, 1.50),
    LEGENDARY(1.75, 3.20, 5.0, 2.00);

    /** Chances in percent with the first customer class (Hobbyists) ... */
    private static final double[] START_WEIGHTS = {62, 25, 9, 3, 1};
    /** ... and with the top class; the classes in between are interpolated. */
    private static final double[] TOP_WEIGHTS = {25, 30, 24, 14, 7};

    private static final ContractRarity[] VALUES = values();

    public final double size, pay, reputation, penalty;

    ContractRarity(double size, double pay, double reputation, double penalty) {
        this.size = size;
        this.pay = pay;
        this.reputation = reputation;
        this.penalty = penalty;
    }

    public String translationKey() {
        return "data_center_addon.tier." + name().toLowerCase(Locale.ROOT);
    }

    public static ContractRarity of(int ordinal) {
        return VALUES[Math.max(0, Math.min(VALUES.length - 1, ordinal))];
    }

    /** Chance of each rarity in percent for an account of this customer class. */
    public static double[] chances(CustomerTier unlocked) {
        double progress = unlocked.ordinal() / (double) (CustomerTier.values().length - 1);
        double[] out = new double[VALUES.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = START_WEIGHTS[i] + (TOP_WEIGHTS[i] - START_WEIGHTS[i]) * progress;
        }
        return out;
    }

    /** Rolls the rarity of a new offer. */
    public static ContractRarity roll(CustomerTier unlocked, RandomSource random) {
        double[] chances = chances(unlocked);
        double total = 0;
        for (double c : chances) {
            total += c;
        }
        double r = random.nextDouble() * total;
        for (int i = 0; i < chances.length; i++) {
            r -= chances[i];
            if (r < 0) {
                return VALUES[i];
            }
        }
        return COMMON;
    }
}
