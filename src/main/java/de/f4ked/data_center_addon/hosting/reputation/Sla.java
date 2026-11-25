package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.wallet.OwnerMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * The service level agreement: at the end of every in-game calendar month an account whose racks ran
 * with an uptime of at least {@link #UPTIME} gets {@link #RENOWN} renown. One bonus per month, not per hour.
 */
public final class Sla {
    static final double UPTIME = 0.999;
    static final double RENOWN = 10;

    private Sla() {
    }

    /** A finished day: adds it to its month; when the month is over, settles it. */
    static void closeDay(ServerLevel level, UUID id, ReputationBook.Account a, double[] day, long closedDay, long today) {
        int month = month(closedDay);
        if (a.slaMonth != month) {
            a.slaMonth = month;
            a.slaServed = 0;
            a.slaDown = 0;
        }
        a.slaServed += day[Ruf.SERVED];
        a.slaDown += day[Ruf.DOWN];
        if (month(today) == month) {
            return;
        }
        double total = a.slaServed + a.slaDown;
        if (total > 0) {
            double uptime = a.slaServed / total;
            String percent = String.format(Locale.ROOT, "%.2f %%", uptime * 100);
            if (uptime >= UPTIME) {
                Renown.add(level, id, RENOWN, Renown.Source.SLA);
                OwnerMessages.toOwner(level.getServer(), id, Component.translatable("data_center_addon.reputation.msg.sla_met",
                        percent, "+" + Math.round(RENOWN)).withStyle(ChatFormatting.GREEN));
            } else {
                OwnerMessages.toOwner(level.getServer(), id, Component.translatable("data_center_addon.reputation.msg.sla_missed",
                        percent).withStyle(ChatFormatting.GRAY));
            }
        }
        a.slaMonth = month(today);
        a.slaServed = 0;
        a.slaDown = 0;
    }

    /** yyyymm of an epoch day. */
    static int month(long epochDay) {
        LocalDate d = LocalDate.ofEpochDay(epochDay);
        return d.getYear() * 100 + d.getMonthValue();
    }
}
