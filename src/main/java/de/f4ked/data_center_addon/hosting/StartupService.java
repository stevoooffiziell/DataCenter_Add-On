package de.f4ked.data_center_addon.hosting;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.hosting.HostingCustomers;
import com.example.datacenter.hosting.HostingModeRates;
import com.example.datacenter.hosting.HostingProduct;
import com.example.datacenter.hosting.HostingRackBlockEntity;
import com.example.datacenter.hosting.HostingReputation;
import com.example.datacenter.wallet.OwnerMessages;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.Data_center_addon;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
 *   <li><b>Prepayment</b>: part of the new customers book an annual plan and pay several months in
 *       advance, at a discount. The money arrives at once and is settled day by day against the income
 *       those customers bring; customers that leave early get the rest back.</li>
 *   <li><b>Milestones</b>: one-time rewards ({@link Milestone}).</li>
 *   <li><b>Service tickets</b>: a client asks someone to look at one of the account's racks;
 *       right-clicking the rack before the deadline pays.</li>
 * </ul>
 * The racks report their income through {@code HostingRackBlockEntityMixin}; everything else runs once
 * a second and once per in-game day. Accounts that already had reputation when the add-on first saw
 * them are established companies: no start bonus, no grant.
 */
@EventBusSubscriber(modid = Data_center_addon.MODID)
public final class StartupService {
    public static final int MAX_TICKETS = 2;
    public static final int TICKET_KINDS = 5;
    private static final int TICKET_DAYS = 2;
    private static final float TICKET_CHANCE = 0.6f;
    /** A rack counts as running if it reported within this many ticks. */
    private static final int RACK_SEEN_TICKS = 6_000;
    private static final int SAMPLE_TICKS = 20;
    private static final int MAX_CATCH_UP_DAYS = 60;

    /** Racks by owner and the server tick they last reported; not saved. */
    private static final Map<UUID, Map<GlobalPos, Integer>> RACKS = new HashMap<>();
    /** Rack income per owner since the last sample, in cents. */
    private static final Map<UUID, Double> INCOME = new HashMap<>();

    public enum Action { TAKE_LOAN, REPAY_LOAN, PREPAY_ON, PREPAY_OFF }

    /** Months a prepaying customer pays in advance: 3 to 12. */
    private static final int PREPAY_MONTHS_MIN = 3;
    private static final int PREPAY_MONTHS_MAX = 12;
    private static final double DAYS_PER_MONTH = 30.4375;

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
            setupFees(level, id, a, date);
            milestones(level, id, a);
            if (a.lastDay < 0) {
                a.lastDay = today;
            } else if (today > a.lastDay) {
                int days = (int) Math.min(MAX_CATCH_UP_DAYS, today - a.lastDay);
                for (int i = 0; i < days; i++) {
                    settleDay(level, id, a, a.lastDay + i + 1);
                }
                a.lastDay = today;
            }
            payOut(level, id, a);
        }
        book.setDirty();
    }

    /** Rack income of one sample: starts the founding phase, earns the start bonus. */
    private static void income(ServerLevel level, UUID id, StartupBook.Account a, double cents, long today) {
        MinecraftServer server = level.getServer();
        if (a.startDay < 0) {
            a.startDay = today;
            a.veteran = HostingReputation.get(level, id).getPoints() >= Config.veteranReputation();
            long grant = Config.foundingGrantCents();
            if (!a.veteran && grant > 0) {
                a.grantCents = grant;
                DataCenterApi.deposit(level, id, grant);
                OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.grant",
                        ContractService.usd(grant)).withStyle(ChatFormatting.GREEN));
            }
            if (!a.veteran && Config.startBonus() > 0) {
                OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.bonus_started",
                        bonusText(Config.startBonus())).withStyle(ChatFormatting.GREEN));
            }
        }
        double factor = bonusFactor(a, today);
        a.revenueCents += cents;
        if (factor > 0) {
            a.bonusOwedCents += cents * factor;
        } else if (!a.veteran && !a.bonusEnded && Config.startBonus() > 0) {
            a.bonusEnded = true;
            OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.bonus_ended",
                    ContractService.usd(a.bonusPaidCents + (long) a.bonusOwedCents)));
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

    /** Whether the account is a young company (no rack income yet, or within the start bonus days). */
    public static boolean earlyPhase(ServerLevel level, UUID id) {
        StartupBook.Account a = StartupBook.get(level.getServer()).accounts().get(id);
        if (a == null || a.startDay < 0) {
            return a == null ? HostingReputation.get(level, id).getPoints() < Config.veteranReputation() : !a.veteran;
        }
        return !a.veteran && DataCenterApi.currentDate(level).toEpochDay() - a.startDay < Config.startBonusDays();
    }

    /** Pays the whole cents of the start bonus and the setup fees earned so far. */
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
        long debt = (long) Math.floor(a.prepayDebtCents);
        if (debt > 0) {
            long take = Math.min(debt, Math.max(0, DataCenterApi.balanceCents(level, id)));
            if (take > 0 && DataCenterApi.withdraw(level, id, take)) {
                a.prepayDebtCents -= take;
            }
        }
    }

    // ------------------------------------------------------------------ setup fees

    /** Customers that joined since the last check pay their setup fee; part of them book an annual plan. */
    private static void setupFees(ServerLevel level, UUID id, StartupBook.Account a, LocalDate date) {
        HostingCustomers customers = HostingCustomers.get(level);
        double months = Config.setupFeeMonths();
        double classMultiplier = HostingReputation.get(level, id).getUnlockedClass().incomeMultiplier;
        double prepayShare = a.prepayEnabled ? Config.prepayShare() : 0;
        HostingProduct[] products = HostingProduct.values();
        for (int i = 0; i < products.length && i < a.lastCustomers.length; i++) {
            double now = customers.total(id, products[i]);
            double before = a.lastCustomers[i];
            if (!Double.isNaN(before) && now > before) {
                double joined = now - before;
                if (months > 0) {
                    a.feeOwedCents += joined * HostingModeRates.unitUsd(products[i], date) * 100 * months * classMultiplier;
                }
                if (prepayShare > 0) {
                    a.prepayPending[i] += joined * prepayShare;
                    if (a.prepayPending[i] >= 1) {
                        int count = (int) Math.floor(a.prepayPending[i]);
                        a.prepayPending[i] -= count;
                        prepay(level, id, a, products[i], count, date, classMultiplier);
                    }
                }
            }
            a.lastCustomers[i] = now;
        }
    }

    /** {@code count} new customers pay several months in advance: the money arrives now, minus the discount. */
    private static void prepay(ServerLevel level, UUID id, StartupBook.Account a, HostingProduct product, int count, LocalDate date,
                               double classMultiplier) {
        int months = PREPAY_MONTHS_MIN + level.getRandom().nextInt(PREPAY_MONTHS_MAX - PREPAY_MONTHS_MIN + 1);
        double value = count * HostingModeRates.unitNet(product, date) * 100 * months * classMultiplier;
        long paid = Math.round(value * (1 - Config.prepayDiscount()));
        if (paid <= 0) {
            return;
        }
        a.prepayments.add(new StartupBook.Prepayment(product, count, value, (int) Math.round(months * DAYS_PER_MONTH)));
        a.prepayReceivedCents += paid;
        DataCenterApi.deposit(level, id, paid);
    }

    /**
     * One day of the running annual plans: customers that left get the rest of their plan back, the
     * others' day is settled against the income they bring.
     */
    private static void settlePrepayments(ServerLevel level, UUID id, StartupBook.Account a) {
        if (a.prepayments.isEmpty()) {
            return;
        }
        HostingCustomers customers = HostingCustomers.get(level);
        for (HostingProduct product : HostingProduct.values()) {
            double prepaid = 0;
            for (StartupBook.Prepayment p : a.prepayments) {
                if (p.product == product) {
                    prepaid += p.customers;
                }
            }
            double now = customers.total(id, product);
            if (prepaid <= 0 || now >= prepaid) {
                continue;
            }
            double left = 1 - now / prepaid;
            for (StartupBook.Prepayment p : a.prepayments) {
                if (p.product == product) {
                    double refund = p.remainingCents * left;
                    a.prepayDebtCents += refund;
                    p.remainingCents -= refund;
                    p.customers *= 1 - left;
                }
            }
        }
        for (Iterator<StartupBook.Prepayment> it = a.prepayments.iterator(); it.hasNext(); ) {
            StartupBook.Prepayment p = it.next();
            double day = p.daysLeft <= 1 ? p.remainingCents : p.remainingCents / p.daysLeft;
            a.prepayDebtCents += day;
            p.remainingCents -= day;
            p.daysLeft--;
            if (p.daysLeft <= 0 || p.remainingCents < 1) {
                a.prepayDebtCents += Math.max(0, p.remainingCents);
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ milestones

    private static void milestones(ServerLevel level, UUID id, StartupBook.Account a) {
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

    /** Marks an event milestone (first contract, delivery, ticket) as reached and pays it. */
    static void award(ServerLevel level, UUID id, Milestone milestone) {
        StartupBook book = StartupBook.get(level.getServer());
        StartupBook.Account a = book.account(id);
        if ((a.milestones & milestone.bit()) == 0) {
            reach(level, id, a, milestone);
            book.setDirty();
        }
    }

    private static void reach(ServerLevel level, UUID id, StartupBook.Account a, Milestone m) {
        a.milestones |= m.bit();
        long cents = rewardCents(m);
        if (cents > 0) {
            DataCenterApi.deposit(level, id, cents);
            OwnerMessages.toOwner(level.getServer(), id, Component.translatable("data_center_addon.startup.msg.milestone",
                    Component.translatable(m.translationKey()), ContractService.usd(cents)).withStyle(ChatFormatting.GREEN));
        }
    }

    static long rewardCents(Milestone m) {
        return Math.round(m.usd * 100.0 * Config.milestoneRewards());
    }

    // ------------------------------------------------------------------ days

    private static void settleDay(ServerLevel level, UUID id, StartupBook.Account a, long day) {
        MinecraftServer server = level.getServer();
        settlePrepayments(level, id, a);
        // founding loan: one instalment a day after the grace period
        if (a.loanOutstandingCents > 0 && day > a.loanStartDay + Config.foundingLoanGraceDays()) {
            long due = Math.min(instalment(a), a.loanOutstandingCents);
            long take = Math.min(due, Math.max(0, DataCenterApi.balanceCents(level, id)));
            if (take > 0 && DataCenterApi.withdraw(level, id, take)) {
                a.loanOutstandingCents -= take;
            }
            if (a.loanOutstandingCents <= 0) {
                OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.loan_repaid")
                        .withStyle(ChatFormatting.GREEN));
            } else if (take < due) {
                OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.loan_short",
                        ContractService.usd(due - take)).withStyle(ChatFormatting.GOLD));
            }
        }

        // service tickets
        for (Iterator<StartupBook.Ticket> it = a.tickets.iterator(); it.hasNext(); ) {
            StartupBook.Ticket ticket = it.next();
            if (ticket.lastDay < day) {
                it.remove();
                OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.ticket_expired",
                        ticket.pos.getX(), ticket.pos.getY(), ticket.pos.getZ()).withStyle(ChatFormatting.GRAY));
            }
        }
        RandomSource random = level.getRandom();
        if (Config.serviceTickets() && a.tickets.size() < MAX_TICKETS && random.nextFloat() < TICKET_CHANCE) {
            GlobalPos rack = runningRack(server, id, a, random);
            if (rack != null) {
                int cls = HostingReputation.get(level, id).getUnlockedClass().ordinal();
                long reward = Math.round((30 + random.nextInt(51)) * (1 + 0.5 * cls)) * 100L;
                StartupBook.Ticket ticket = new StartupBook.Ticket(a.nextTicketId++, rack.dimension().location(), rack.pos(),
                        random.nextInt(TICKET_KINDS), reward, day + TICKET_DAYS - 1);
                a.tickets.add(ticket);
                OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.ticket_new",
                        Component.translatable("data_center_addon.startup.ticket.kind." + ticket.kind), ticket.pos.getX(),
                        ticket.pos.getY(), ticket.pos.getZ(), ContractService.usd(reward)).withStyle(ChatFormatting.AQUA));
            }
        }
    }

    /** A random rack of the account that reported recently and has no open ticket. */
    private static GlobalPos runningRack(MinecraftServer server, UUID id, StartupBook.Account a, RandomSource random) {
        Map<GlobalPos, Integer> racks = RACKS.get(id);
        if (racks == null) {
            return null;
        }
        List<GlobalPos> candidates = new ArrayList<>();
        for (Map.Entry<GlobalPos, Integer> entry : racks.entrySet()) {
            GlobalPos pos = entry.getKey();
            boolean taken = a.tickets.stream().anyMatch(t -> t.pos.equals(pos.pos()) && t.dimension.equals(pos.dimension().location()));
            if (!taken && server.getTickCount() - entry.getValue() <= RACK_SEEN_TICKS) {
                candidates.add(pos);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    private static long instalment(StartupBook.Account a) {
        int days = Config.foundingLoanRepayDays();
        return (a.loanCents + days - 1) / days;
    }

    /** Right-clicking a rack with an open service ticket of the player's account closes it and pays. */
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        UUID id = DataCenterApi.accountOf(level, event.getEntity().getUUID());
        StartupBook book = StartupBook.get(level.getServer());
        StartupBook.Account a = book.accounts().get(id);
        if (a == null || a.tickets.isEmpty()) {
            return;
        }
        BlockPos pos = event.getPos();
        for (Iterator<StartupBook.Ticket> it = a.tickets.iterator(); it.hasNext(); ) {
            StartupBook.Ticket ticket = it.next();
            if (ticket.pos.equals(pos) && ticket.dimension.equals(level.dimension().location())) {
                it.remove();
                DataCenterApi.deposit(level, id, ticket.rewardCents);
                OwnerMessages.toOwner(level.getServer(), id, Component.translatable("data_center_addon.startup.msg.ticket_done",
                        Component.translatable("data_center_addon.startup.ticket.kind." + ticket.kind),
                        ContractService.usd(ticket.rewardCents)).withStyle(ChatFormatting.GREEN));
                book.setDirty();
                award(level, id, Milestone.FIRST_TICKET);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ actions

    static void apply(ServerLevel level, UUID id, Action action) {
        MinecraftServer server = level.getServer();
        StartupBook book = StartupBook.get(server);
        StartupBook.Account a = book.account(id);
        switch (action) {
            case TAKE_LOAN -> {
                long amount = Config.foundingLoanCents();
                if (a.loanTaken || amount <= 0) {
                    return;
                }
                a.loanTaken = true;
                a.loanCents = amount;
                a.loanOutstandingCents = amount;
                a.loanStartDay = DataCenterApi.currentDate(level).toEpochDay();
                DataCenterApi.deposit(level, id, amount);
                OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.loan_taken",
                        ContractService.usd(amount), Config.foundingLoanGraceDays(), Config.foundingLoanRepayDays())
                        .withStyle(ChatFormatting.GREEN));
            }
            case REPAY_LOAN -> {
                long take = Math.min(a.loanOutstandingCents, Math.max(0, DataCenterApi.balanceCents(level, id)));
                if (take <= 0 || !DataCenterApi.withdraw(level, id, take)) {
                    return;
                }
                a.loanOutstandingCents -= take;
                OwnerMessages.toOwner(server, id, a.loanOutstandingCents <= 0
                        ? Component.translatable("data_center_addon.startup.msg.loan_repaid").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("data_center_addon.startup.msg.loan_partly", ContractService.usd(take),
                        ContractService.usd(a.loanOutstandingCents)));
            }
            case PREPAY_ON -> a.prepayEnabled = true;
            case PREPAY_OFF -> {
                a.prepayEnabled = false;
                java.util.Arrays.fill(a.prepayPending, 0);
            }
        }
        book.setDirty();
    }

    // ------------------------------------------------------------------ snapshot

    static HostingSnapshot.Startup snapshot(ServerLevel level, UUID id) {
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
        long instalment = a.loanTaken ? instalment(a)
                : (loanOffer + Config.foundingLoanRepayDays() - 1) / Config.foundingLoanRepayDays();
        List<Long> rewards = new ArrayList<>();
        for (Milestone m : Milestone.values()) {
            rewards.add(rewardCents(m));
        }
        List<HostingSnapshot.Ticket> tickets = new ArrayList<>();
        for (StartupBook.Ticket t : a.tickets) {
            tickets.add(new HostingSnapshot.Ticket(t.id, t.pos, t.kind, t.rewardCents, (int) Math.max(0, t.lastDay - today + 1)));
        }
        double prepayOpen = a.prepayDebtCents;
        double prepayCustomers = 0;
        for (StartupBook.Prepayment p : a.prepayments) {
            prepayOpen += p.remainingCents;
            prepayCustomers += p.customers;
        }
        HostingSnapshot.Prepay prepay = new HostingSnapshot.Prepay(Config.prepayShare() > 0, a.prepayEnabled,
                (int) Math.round(Config.prepayShare() * 100), (int) Math.round(Config.prepayDiscount() * 100), a.prepayReceivedCents,
                Math.round(prepayOpen), (int) Math.round(prepayCustomers));
        return new HostingSnapshot.Startup(started, veteran, factor, Config.startBonusMode().ordinal(), daysLeft, revenueLeft,
                a.bonusPaidCents, a.grantCents, veteran ? 0 : Config.foundingGrantCents(), a.feesPaidCents, loanOffer, a.loanTaken,
                a.loanOutstandingCents, grace, instalment, a.milestones, List.copyOf(rewards), List.copyOf(tickets), prepay);
    }

    /** "+200 %" */
    static String bonusText(double factor) {
        return "+" + Math.round(factor * 100) + " %";
    }
}
