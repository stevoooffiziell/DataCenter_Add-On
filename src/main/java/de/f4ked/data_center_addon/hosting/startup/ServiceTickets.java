package de.f4ked.data_center_addon.hosting.startup;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.hosting.HostingRackBlockEntity;
import com.example.datacenter.hosting.HostingReputation;
import com.example.datacenter.wallet.OwnerMessages;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import de.f4ked.data_center_addon.hosting.Money;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service tickets: now and then a client asks someone to look at one of the account's running racks.
 * Right-clicking that rack before the deadline pays at once; an expired ticket costs nothing.
 */
@EventBusSubscriber(modid = Data_center_addon.MODID)
final class ServiceTickets {
    private static final int MAX_OPEN = 2;
    private static final int KINDS = 5;
    /** Days a ticket stays open: today and tomorrow. */
    private static final int DAYS = 2;
    private static final float CHANCE_PER_DAY = 0.6f;
    /** A rack counts as running if it reported within this many ticks. */
    private static final int RACK_SEEN_TICKS = 6_000;

    private ServiceTickets() {
    }

    /**
     * Ends one in-game day: drops expired tickets, maybe opens a new one. Days caught up after a time
     * skip ({@code today} false) open no tickets and send no messages.
     */
    static void settleDay(ServerLevel level, UUID id, StartupBook.Account a, long day, boolean today) {
        MinecraftServer server = level.getServer();
        for (Iterator<StartupBook.Ticket> it = a.tickets.iterator(); it.hasNext(); ) {
            StartupBook.Ticket ticket = it.next();
            if (ticket.lastDay < day) {
                it.remove();
                if (today) {
                    OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.ticket_expired",
                            ticket.pos.getX(), ticket.pos.getY(), ticket.pos.getZ()).withStyle(ChatFormatting.GRAY));
                }
            }
        }
        RandomSource random = level.getRandom();
        if (!today || !Config.serviceTickets() || a.tickets.size() >= MAX_OPEN || random.nextFloat() >= CHANCE_PER_DAY) {
            return;
        }
        GlobalPos rack = runningRack(server, id, a, random);
        if (rack == null) {
            return;
        }
        int cls = HostingReputation.get(level, id).getUnlockedClass().ordinal();
        long reward = Math.round((30 + random.nextInt(51)) * (1 + 0.5 * cls)) * 100L;
        StartupBook.Ticket ticket = new StartupBook.Ticket(a.nextTicketId++, rack.dimension().location(), rack.pos(),
                random.nextInt(KINDS), reward, day + DAYS - 1);
        a.tickets.add(ticket);
        OwnerMessages.toOwner(server, id, Component.translatable("data_center_addon.startup.msg.ticket_new", kindName(ticket),
                ticket.pos.getX(), ticket.pos.getY(), ticket.pos.getZ(), Money.usd(reward)).withStyle(ChatFormatting.AQUA));
    }

    /** A random rack of the account that reported recently and has no open ticket. */
    private static GlobalPos runningRack(MinecraftServer server, UUID id, StartupBook.Account a, RandomSource random) {
        List<GlobalPos> candidates = new ArrayList<>();
        for (Map.Entry<GlobalPos, Integer> entry : StartupService.racksOf(id).entrySet()) {
            GlobalPos pos = entry.getKey();
            boolean taken = a.tickets.stream().anyMatch(t -> t.pos.equals(pos.pos()) && t.dimension.equals(pos.dimension().location()));
            if (!taken && server.getTickCount() - entry.getValue() <= RACK_SEEN_TICKS) {
                candidates.add(pos);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    /** Right-clicking a rack with an open ticket of the player's account closes it and pays. */
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
            if (ticket.pos.equals(pos) && ticket.dimension.equals(level.dimension().location())
                    && level.getBlockEntity(pos) instanceof HostingRackBlockEntity) {
                it.remove();
                DataCenterApi.deposit(level, id, ticket.rewardCents);
                OwnerMessages.toOwner(level.getServer(), id, Component.translatable("data_center_addon.startup.msg.ticket_done",
                        kindName(ticket), Money.usd(ticket.rewardCents)).withStyle(ChatFormatting.GREEN));
                book.setDirty();
                Milestones.award(level, id, Milestone.FIRST_TICKET);
                return;
            }
        }
    }

    private static Component kindName(StartupBook.Ticket ticket) {
        return Component.translatable("data_center_addon.startup.ticket.kind." + ticket.kind);
    }

    static List<HostingSnapshot.Ticket> snapshot(StartupBook.Account a, long today) {
        List<HostingSnapshot.Ticket> tickets = new ArrayList<>();
        for (StartupBook.Ticket t : a.tickets) {
            tickets.add(new HostingSnapshot.Ticket(t.id, t.pos, t.kind, t.rewardCents, (int) Math.max(0, t.lastDay - today + 1)));
        }
        return List.copyOf(tickets);
    }
}
