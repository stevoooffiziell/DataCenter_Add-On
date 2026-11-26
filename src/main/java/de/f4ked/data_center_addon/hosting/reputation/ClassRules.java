package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.hosting.CustomerClass;
import com.example.datacenter.hosting.HostingReputation;
import com.example.datacenter.wallet.OwnerMessages;
import de.f4ked.data_center_addon.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Which customer class ({@link CustomerTier}) an account has unlocked, from both values: the Ruf
 * (quality) and the renown (Bekanntheit). A class becomes a goal to work for instead of a time to wait.
 * <p>
 * The room certification each class needs stays as in Data Center and goes up to Tier IV for the top
 * classes: a rack only serves a class its room's tier allows.
 * <p>
 * Once unlocked, a class is kept while the Ruf stays within {@link #RUF_MARGIN} and the renown within
 * {@link #RENOWN_MARGIN} of its requirement.
 */
public final class ClassRules {
    /** Highest renown with the reputation model on (Data Center's own limit is 1000). */
    public static final double MAX_RENOWN = 2500;
    static final double RUF_MARGIN = 0.05, RENOWN_MARGIN = 25;

    private ClassRules() {
    }

    /** The renown limit that applies now. */
    public static double maxRenown() {
        return Config.reputationModel() ? MAX_RENOWN : HostingReputation.MAX_POINTS;
    }

    public static boolean meets(CustomerTier t, double ruf, double renown) {
        return ruf >= t.ruf - 1e-9 && renown >= t.renown;
    }

    /** The class the account holds now, given the class it had. */
    public static CustomerTier tierFor(double ruf, double renown, CustomerTier current) {
        CustomerTier best = CustomerTier.HOBBYISTS;
        for (CustomerTier t : CustomerTier.values()) {
            if (meets(t, ruf, renown)) {
                best = t;
            }
        }
        if (best.ordinal() < current.ordinal()
                && ruf >= current.ruf - RUF_MARGIN && renown >= current.renown - RENOWN_MARGIN) {
            return current;
        }
        return best;
    }

    /**
     * The account's class. With the reputation model off it follows Data Center's class (the first class
     * of it).
     */
    public static CustomerTier tier(MinecraftServer server, @Nullable UUID account) {
        CustomerClass base = HostingReputation.get(server.overworld(), account).getUnlockedClass();
        if (!Config.reputationModel()) {
            return CustomerTier.first(base);
        }
        ReputationBook.Account a = ReputationBook.get(server).account(account);
        return a.tier < 0 ? CustomerTier.first(base) : CustomerTier.of(a.tier);
    }

    /**
     * The class to unlock next, or null at the top. With the reputation model off only the first class of
     * each Data Center class counts.
     */
    @Nullable
    public static CustomerTier next(CustomerTier current) {
        if (Config.reputationModel()) {
            return current.next();
        }
        for (CustomerTier t : CustomerTier.values()) {
            if (t.base.ordinal() > current.base.ordinal()) {
                return t;
            }
        }
        return null;
    }

    /**
     * Re-checks the account's class (Data Center asks whenever the points change, and once a second) and
     * tells the owner about a new one. Returns the Data Center class it belongs to.
     *
     * @param current Data Center's class of the account so far
     */
    public static CustomerClass update(MinecraftServer server, @Nullable UUID account, double ruf, double renown,
                                       CustomerClass current) {
        ReputationBook book = ReputationBook.get(server);
        ReputationBook.Account a = book.account(account);
        CustomerTier before = a.tier < 0 ? CustomerTier.first(current) : CustomerTier.of(a.tier);
        CustomerTier now = tierFor(ruf, renown, before);
        if (a.tier != now.ordinal()) {
            if (a.tier >= 0 && account != null) {
                boolean up = now.ordinal() > before.ordinal();
                OwnerMessages.toOwner(server, account, Component.translatable("news.prefix").append(Component.translatable(
                        up ? "data_center_addon.reputation.tier_up" : "data_center_addon.reputation.tier_down",
                        Component.translatable(now.translationKey()), Math.round((now.income - 1) * 100))));
            }
            a.tier = now.ordinal();
            book.setDirty();
        }
        return now.base;
    }
}
