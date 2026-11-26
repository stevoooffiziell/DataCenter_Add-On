package de.f4ked.data_center_addon.hosting.startup;

import de.f4ked.data_center_addon.hosting.reputation.ClassRules;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.hosting.HostingCustomers;
import com.example.datacenter.hosting.HostingModeRates;
import com.example.datacenter.hosting.HostingProduct;
import com.example.datacenter.hosting.HostingRackBlockEntity;
import com.example.datacenter.hosting.HostingReputation;
import com.example.datacenter.wallet.OwnerMessages;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import de.f4ked.data_center_addon.hosting.Money;
import de.f4ked.data_center_addon.hosting.market.MarketService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The founding phase of a hosting company: help to get the first money together.
 * <ul>
 *   <li><b>Start bonus</b>: an extra share of every hosting rack's income, paid on top. It starts with
 *       the first rack income and fades out over a number of days or ends at a revenue
 *       ({@link Config#startBonusMode()}).</li>
 *   <li><b>Founding grant</b>: paid once with the first rack income.</li>
 *   <li><b>Founding loan</b>: interest-free, taken once in the Hosting Manager, repaid in daily
 *       instalments after a grace period.</li>
 *   <li><b>Setup fees</b>: every new customer pays a one-time fee.</li>
 *   <li><b>Prepayment</b> ({@link Prepayments}), <b>milestones</b> ({@link Milestones}) and
 *       <b>service tickets</b> ({@link ServiceTickets}).</li>
 * </ul>
 * The racks report their income through {@code HostingRackBlockEntityMixin}; everything else runs once
 * a second and once per in-game day. Accounts that already had reputation when the add-on first saw
 * them are established companies: no start bonus, no grant.
 */
@EventBusSubscriber(modid = Data_center_addon.MODID)
public final class StartupService {
    private static final int SAMPLE_TICKS = 20;
    private static final int MAX_CATCH_UP_DAYS = 60;

    /** Racks by owner and the server tick they last reported; not saved. */
    private static final Map<UUID, Map<GlobalPos, Integer>> RACKS = new HashMap<>();
    /** Rack income per owner since the last sample, in cents. */
    private static final Map<UUID, Double> INCOME = new HashMap<>();

    public enum Action { TAKE_LOAN, REPAY_LOAN, PREPAY_ON, PREPAY_OFF }

    private StartupService() {
    }

    // ------------------------------------------------------------------ racks

    /** Called by every hosting rack after its loadout ran (once per tick while it works). */
    public static void onRackTick(HostingRackBlockEntity rack) {
        if (!(rack.getLevel() instanceof ServerLevel level)) {
            return;
        }
        UUID owner = OwnerMessages.ownerOf(level, rack.getBlockPos());
        if (owner == null) {
            return;
        }
        RACKS.computeIfAbsent(owner, o -> new HashMap<>())
                .put(GlobalPos.of(level.dimension(), rack.getBlockPos()), level.getServer().getTickCount());
        double cents = rack.getLastUsdCentsPerTick();
        if (cents > 0) {
            INCOME.merge(owner, cents, Double::sum);
        }
    }

    /** The account's racks and the server tick each last reported. */
    static Map<GlobalPos, Integer> racksOf(UUID id) {
        return RACKS.getOrDefault(id, Map.of());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        RACKS.clear();
        INCOME.clear();
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SAMPLE_TICKS != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        StartupBook book = StartupBook.get(server);
        LocalDate date = DataCenterApi.currentDate(level);
        long today = date.toEpochDay();

        for (Map.Entry<UUID, Double> entry : INCOME.entrySet()) {
            income(level, entry.getKey(), book.account(entry.getKey()), entry.getValue(), today);
        }
        INCOME.clear();

        Set<UUID> ids = new HashSet<>(book.accounts().keySet());
        ids.addAll(RACKS.keySet());
        for (UUID id : ids) {
            StartupBook.Account a = book.account(id);
            newCustomers(level, id, a, date);
            Milestones.check(level, id, a);
            if (a.lastDay < 0) {
                a.lastDay = today;
            } else if (today > a.lastDay) {
                int days = (int) Math.min(MAX_CATCH_UP_DAYS, today - a.lastDay);
                for (int i = 0; i < days; i++) {
                    long day = a.lastDay + i + 1;
                    settleDay(level, id, a, day, day == today);
                }
                a.lastDay = today;
            }
            payOut(level, id, a);
        }
        book.setDirty();
    }

    /** {@code today}: the last of the days being settled (earlier ones are caught up after a time skip). */
    private static void settleDay(ServerLevel level, UUID id, StartupBook.Account a, long day, boolean today) {
        Prepayments.settleDay(level, id, a);
        settleLoan(level, id, a, day, today);
        ServiceTickets.settleDay(level, id, a, day, today);
    }

    // ------------------------------------------------------------------ start bonus and grant

    /** Rack income of one sample: starts the founding phase, earns the start bonus. */
    private static void income(ServerLevel level, UUID id, StartupBook.Account a, double cents, long today) {
        MinecraftServer server = level.getServer();
        if (a.startDay < 0) {
            start(level, id, a, today);
        }
        double factor = bonusFactor(a, today);
        a.revenueCents += cents;
        if (factor > 0) {
            a.bonusOwedCents += cents * factor;
        } else if (!a.veteran && !a.bonusEnded && Config.startBonus() > 0) {
            a.bonusEnded = true;
            OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.bonus_ended",
                    Money.usd(a.bonusPaidCents + (long) a.bonusOwedCents)));
        }
    }

    /** The first rack income: decides whether the company is established, pays the grant. */
    private static void start(ServerLevel level, UUID id, StartupBook.Account a, long today) {
        MinecraftServer server = level.getServer();
        a.startDay = today;
        a.veteran = HostingReputation.get(level, id).getPoints() >= Config.veteranReputation();
        if (a.veteran) {
            return;
        }
        long grant = Config.foundingGrantCents();
        if (grant > 0) {
            a.grantCents = grant;
            DataCenterApi.deposit(level, id, grant);
            OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.grant", Money.usd(grant))
                    .withStyle(ChatFormatting.GREEN));
        }
        if (Config.startBonus() > 0) {
            OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.bonus_started",
                    "+" + Math.round(Config.startBonus() * 100) + " %").withStyle(ChatFormatting.GREEN));
        }
    }

    /** Extra share of rack income paid now; 0 when the start bonus is over or does not apply. */
    static double bonusFactor(StartupBook.Account a, long today) {
        double bonus = Config.startBonus();
        if (a.startDay < 0 || a.veteran || bonus <= 0) {
            return 0;
        }
        return switch (Config.startBonusMode()) {
            case TIME -> bonus * Math.max(0, 1 - (today - a.startDay) / (double) Config.startBonusDays());
            case REVENUE -> a.revenueCents < Config.startBonusRevenueCents() ? bonus : 0;
        };
    }

    /** Whether the account is a young company: no rack income yet, or the start bonus period (days or revenue) runs. */
    public static boolean earlyPhase(ServerLevel level, UUID id) {
        StartupBook.Account a = StartupBook.get(level.getServer()).accounts().get(id);
        if (a == null || a.startDay < 0) {
            return a == null ? HostingReputation.get(level, id).getPoints() < Config.veteranReputation() : !a.veteran;
        }
        if (a.veteran) {
            return false;
        }
        return switch (Config.startBonusMode()) {
            case TIME -> DataCenterApi.currentDate(level).toEpochDay() - a.startDay < Config.startBonusDays();
            case REVENUE -> a.revenueCents < Config.startBonusRevenueCents();
        };
    }

    /** Pays the whole cents of the start bonus and the setup fees earned so far, takes the prepayment owed. */
    private static void payOut(ServerLevel level, UUID id, StartupBook.Account a) {
        long bonus = (long) Math.floor(a.bonusOwedCents);
        if (bonus > 0) {
            DataCenterApi.deposit(level, id, bonus);
            a.bonusOwedCents -= bonus;
            a.bonusPaidCents += bonus;
        }
        long fees = (long) Math.floor(a.feeOwedCents);
        if (fees > 0) {
            DataCenterApi.deposit(level, id, fees);
            a.feeOwedCents -= fees;
            a.feesPaidCents += fees;
        }
        Prepayments.collect(level, id, a);
    }

    // ------------------------------------------------------------------ new customers

    /**
     * Customers above the most the product ever had pay their setup fee; part of them book an annual
     * plan. Customers who come back after an outage, a move or a lower fill do not pay again.
     */
    private static void newCustomers(ServerLevel level, UUID id, StartupBook.Account a, LocalDate date) {
        HostingCustomers customers = HostingCustomers.get(level);
        double months = Config.setupFeeMonths();
        double classMultiplier = ClassRules.tier(level.getServer(), id).income;
        HostingProduct[] products = HostingProduct.values();
        for (int i = 0; i < products.length && i < a.lastCustomers.length; i++) {
            double now = customers.total(id, products[i]);
            double before = a.lastCustomers[i];
            if (!Double.isNaN(before) && now > before) {
                double joined = now - before;
                // the account's own price (Market tab) counts for the fee and the annual plans
                double multiplier = classMultiplier * MarketService.priceFactor(level, id, products[i]);
                if (months > 0) {
                    a.feeOwedCents += joined * HostingModeRates.unitUsd(products[i], date) * 100 * months * multiplier;
                }
                Prepayments.joined(level, id, a, products[i], joined, date, multiplier);
            }
            if (Double.isNaN(before) || now > before) {
                a.lastCustomers[i] = now;
            }
        }
    }

    // ------------------------------------------------------------------ founding loan

    /** One instalment a day after the grace period, as far as the wallet allows. */
    private static void settleLoan(ServerLevel level, UUID id, StartupBook.Account a, long day, boolean today) {
        if (a.loanOutstandingCents <= 0 || day <= a.loanStartDay + Config.foundingLoanGraceDays()) {
            return;
        }
        MinecraftServer server = level.getServer();
        long due = Math.min(instalment(a), a.loanOutstandingCents);
        long take = Math.min(due, Math.max(0, DataCenterApi.balanceCents(level, id)));
        if (take > 0 && DataCenterApi.withdraw(level, id, take)) {
            a.loanOutstandingCents -= take;
        }
        if (a.loanOutstandingCents <= 0) {
            OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.loan_repaid")
                    .withStyle(ChatFormatting.GREEN));
        } else if (take < due && today) {
            OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.loan_short",
                    Money.usd(due - take)).withStyle(ChatFormatting.GOLD));
        }
    }

    private static long instalment(StartupBook.Account a) {
        return instalment(a.loanCents);
    }

    private static long instalment(long loanCents) {
        int days = Config.foundingLoanRepayDays();
        return (loanCents + days - 1) / days;
    }

    private static void takeLoan(ServerLevel level, UUID id, StartupBook.Account a) {
        long amount = Config.foundingLoanCents();
        if (a.loanTaken || amount <= 0) {
            return;
        }
        a.loanTaken = true;
        a.loanCents = amount;
        a.loanOutstandingCents = amount;
        a.loanStartDay = DataCenterApi.currentDate(level).toEpochDay();
        DataCenterApi.deposit(level, id, amount);
        OwnerMessages.toOwner(level.getServer(), id, Component.translatable("data_center_addon.startup.msg.loan_taken",
                Money.usd(amount), Config.foundingLoanGraceDays(), Config.foundingLoanRepayDays()).withStyle(ChatFormatting.GREEN));
    }

    private static void repayLoan(ServerLevel level, UUID id, StartupBook.Account a) {
        long take = Math.min(a.loanOutstandingCents, Math.max(0, DataCenterApi.balanceCents(level, id)));
        if (take <= 0 || !DataCenterApi.withdraw(level, id, take)) {
            return;
        }
        a.loanOutstandingCents -= take;
        OwnerMessages.toOwner(level.getServer(), id, a.loanOutstandingCents <= 0
                ? Component.translatable("data_center_addon.startup.msg.loan_repaid").withStyle(ChatFormatting.GREEN)
                : Component.translatable("data_center_addon.startup.msg.loan_partly", Money.usd(take), Money.usd(a.loanOutstandingCents)));
    }

    // ------------------------------------------------------------------ actions and snapshot

    public static void apply(ServerLevel level, UUID id, Action action) {
        StartupBook book = StartupBook.get(level.getServer());
        StartupBook.Account a = book.account(id);
        switch (action) {
            case TAKE_LOAN -> takeLoan(level, id, a);
            case REPAY_LOAN -> repayLoan(level, id, a);
            case PREPAY_ON -> Prepayments.setEnabled(a, true);
            case PREPAY_OFF -> Prepayments.setEnabled(a, false);
        }
        book.setDirty();
    }

    public static HostingSnapshot.Startup snapshot(ServerLevel level, UUID id) {
        StartupBook.Account a = StartupBook.get(level.getServer()).account(id);
        long today = DataCenterApi.currentDate(level).toEpochDay();
        boolean started = a.startDay >= 0;
        boolean veteran = started ? a.veteran : HostingReputation.get(level, id).getPoints() >= Config.veteranReputation();
        double factor = started ? bonusFactor(a, today) : veteran ? 0 : Config.startBonus();
        int daysLeft = started ? (int) Math.max(0, Config.startBonusDays() - (today - a.startDay)) : Config.startBonusDays();
        long revenueLeft = (long) Math.max(0, Config.startBonusRevenueCents() - a.revenueCents);
        long loanOffer = Config.foundingLoanCents();
        int grace = a.loanTaken ? (int) Math.max(0, a.loanStartDay + Config.foundingLoanGraceDays() - today)
                : Config.foundingLoanGraceDays();
        long instalment = a.loanTaken ? instalment(a) : instalment(loanOffer);
        List<Long> rewards = new ArrayList<>();
        for (Milestone m : Milestone.values()) {
            rewards.add(Milestones.rewardCents(m));
        }
        return new HostingSnapshot.Startup(started, veteran, factor, Config.startBonusMode().ordinal(), daysLeft, revenueLeft,
                a.bonusPaidCents, a.grantCents, veteran ? 0 : Config.foundingGrantCents(), a.feesPaidCents, loanOffer, a.loanTaken,
                a.loanOutstandingCents, grace, instalment, a.milestones, List.copyOf(rewards), ServiceTickets.snapshot(a, today),
                Prepayments.snapshot(a));
    }
}
