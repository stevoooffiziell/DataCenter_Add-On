package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.wallet.OwnerMessages;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import de.f4ked.data_center_addon.hosting.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Advertising: money for renown. Which campaigns exist depends on the era: banners and magazine ads
 * around 2000, search ads from 2001, social media from 2008, video and influencers from 2014; a stand at
 * a trade fair always. Each campaign can be booked once every {@link #COOLDOWN_DAYS} in-game days.
 */
public final class Campaigns {
    static final int COOLDOWN_DAYS = 7;

    /** A kind of campaign; the name gives {@code data_center_addon.reputation.campaign.<name>}. */
    public enum Campaign {
        BANNER(500, 4, LocalDate.of(1990, 1, 1), LocalDate.of(2006, 1, 1)),
        MAGAZINE(1_500, 10, LocalDate.of(1990, 1, 1), LocalDate.of(2013, 1, 1)),
        SEARCH(800, 6, LocalDate.of(2001, 1, 1), null),
        SOCIAL(1_000, 8, LocalDate.of(2008, 1, 1), null),
        VIDEO(2_500, 18, LocalDate.of(2014, 1, 1), null),
        FAIR(5_000, 35, LocalDate.of(1990, 1, 1), null);

        public final int usd;
        public final int renown;
        private final LocalDate from;
        private final LocalDate until;

        Campaign(int usd, int renown, LocalDate from, LocalDate until) {
            this.usd = usd;
            this.renown = renown;
            this.from = from;
            this.until = until;
        }

        public boolean existsOn(LocalDate date) {
            return !date.isBefore(from) && (until == null || date.isBefore(until));
        }

        public String key() {
            return "data_center_addon.reputation.campaign." + name().toLowerCase(Locale.ROOT);
        }
    }

    private Campaigns() {
    }

    /** Books a campaign: takes the money and gives the renown, if it exists, is not cooling down and the wallet holds it. */
    public static void book(ServerLevel level, UUID id, int ordinal) {
        Campaign[] all = Campaign.values();
        if (!Config.reputationModel() || ordinal < 0 || ordinal >= all.length) {
            return;
        }
        Campaign c = all[ordinal];
        LocalDate date = DataCenterApi.currentDate(level);
        ReputationBook book = ReputationBook.get(level.getServer());
        ReputationBook.Account a = book.account(id);
        long cents = c.usd * 100L;
        if (!c.existsOn(date) || daysLeft(a, c, date.toEpochDay()) > 0 || DataCenterApi.balanceCents(level, id) < cents
                || !DataCenterApi.withdraw(level, id, cents)) {
            return;
        }
        a.campaignDays[ordinal] = date.toEpochDay();
        book.setDirty();
        Renown.add(level, id, c.renown, Renown.Source.ADVERTISING);
        OwnerMessages.toOwner(level.getServer(), id, Component.translatable("data_center_addon.reputation.msg.campaign",
                Component.translatable(c.key()), Money.usd(cents), "+" + c.renown).withStyle(ChatFormatting.GREEN));
    }

    private static int daysLeft(ReputationBook.Account a, Campaign c, long today) {
        long last = a.campaignDays[c.ordinal()];
        return last < 0 ? 0 : (int) Math.max(0, last + COOLDOWN_DAYS - today);
    }

    /** The campaigns that exist today, with their cooldown. */
    static List<HostingSnapshot.Campaign> snapshot(ReputationBook.Account a, LocalDate date) {
        List<HostingSnapshot.Campaign> out = new ArrayList<>();
        if (!Config.reputationModel()) {
            return out;
        }
        for (Campaign c : Campaign.values()) {
            if (c.existsOn(date)) {
                out.add(new HostingSnapshot.Campaign(c.ordinal(), c.usd * 100L, c.renown, daysLeft(a, c, date.toEpochDay())));
            }
        }
        return out;
    }
}
