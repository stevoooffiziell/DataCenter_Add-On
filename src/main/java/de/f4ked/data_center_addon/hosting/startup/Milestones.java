package de.f4ked.data_center_addon.hosting.startup;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.hosting.HostingCustomers;
import com.example.datacenter.hosting.HostingProduct;
import com.example.datacenter.hosting.HostingReputation;
import com.example.datacenter.wallet.OwnerMessages;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.hosting.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Checks and pays the {@link Milestone}s. Customer and class milestones are checked once a second;
 * event milestones (first contract, delivery, ticket) are awarded by the code that finishes them. The
 * first check of an account marks what it already has without paying, so an update brings no windfall.
 */
public final class Milestones {
    private Milestones() {
    }

    /** Marks an event milestone (first contract, delivery, ticket) as reached and pays it. */
    public static void award(ServerLevel level, UUID id, Milestone milestone) {
        StartupBook book = StartupBook.get(level.getServer());
        StartupBook.Account a = book.account(id);
        if ((a.milestones & milestone.bit()) == 0) {
            reach(level, id, a, milestone);
            book.setDirty();
        }
    }

    /** Customer and class milestones the account has met since the last check. */
    static void check(ServerLevel level, UUID id, StartupBook.Account a) {
        HostingCustomers book = HostingCustomers.get(level);
        double customers = 0;
        for (HostingProduct product : HostingProduct.values()) {
            customers += book.total(id, product);
        }
        int cls = HostingReputation.get(level, id).getUnlockedClass().ordinal();
        for (Milestone m : Milestone.values()) {
            if ((a.milestones & m.bit()) != 0) {
                continue;
            }
            boolean met = switch (m.kind) {
                case CUSTOMERS -> customers >= m.threshold;
                case CLASS -> cls >= m.threshold;
                case EVENT -> false;
            };
            if (met) {
                if (a.milestonesSeeded) {
                    reach(level, id, a, m);
                } else {
                    a.milestones |= m.bit();
                }
            }
        }
        a.milestonesSeeded = true;
    }

    private static void reach(ServerLevel level, UUID id, StartupBook.Account a, Milestone m) {
        a.milestones |= m.bit();
        long cents = rewardCents(m);
        if (cents > 0) {
            DataCenterApi.deposit(level, id, cents);
            OwnerMessages.toOwner(level.getServer(), id, Component.translatable("data_center_addon.startup.msg.milestone",
                    Component.translatable(m.translationKey()), Money.usd(cents)).withStyle(ChatFormatting.GREEN));
        }
    }

    /** The reward of a milestone with the config multiplier. */
    static long rewardCents(Milestone m) {
        return Math.round(m.usd * 100.0 * Config.milestoneRewards());
    }
}
