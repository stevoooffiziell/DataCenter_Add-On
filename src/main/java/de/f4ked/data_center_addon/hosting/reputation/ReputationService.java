package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.api.DataCenterApi;
import com.example.datacenter.hosting.HostingReputation;
import de.f4ked.data_center_addon.mixin.HostingReputationInvoker;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import de.f4ked.data_center_addon.hosting.market.MarketBook;
import de.f4ked.data_center_addon.hosting.market.ServiceQuality;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The add-on's reputation model: records each account's service into its day ({@link RackService}
 * reports every rack tick), closes the day once the in-game date moves on, and computes the
 * {@link Ruf} of the last 30 days. Once a second it lets Data Center re-check each account's customer
 * class, which now depends on the Ruf too ({@link ClassRules}).
 */
@EventBusSubscriber(modid = Data_center_addon.MODID)
public final class ReputationService {
    private static final int SAMPLE_TICKS = 20;

    private ReputationService() {
    }

    /** One rack's service of one tick. */
    static void record(MinecraftServer server, @Nullable UUID owner, double earning, double down, double overload,
                       boolean throttled) {
        if (earning + down <= 0) {
            return;
        }
        double[] today = ReputationBook.get(server).account(owner).today;
        today[Ruf.SERVED] += Math.max(0, earning);
        today[Ruf.DOWN] += Math.max(0, down);
        today[Ruf.OVERLOAD] += Math.max(0, Math.min(earning, overload));
        today[Ruf.THROTTLED] += throttled ? Math.max(0, earning) : 0;
    }

    /** A rack of the account crashed: a serious incident that presses the Ruf for 30 days. */
    public static void crash(MinecraftServer server, @Nullable UUID owner) {
        ReputationBook book = ReputationBook.get(server);
        ReputationBook.Account a = book.account(owner);
        a.today[Ruf.CRASHES]++;
        a.incidents.addLast(a.day < 0 ? DataCenterApi.currentDate(server.overworld()).toEpochDay() : a.day);
        book.setDirty();
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        QuickFix.clear();
        Sensitivity.clear();
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SAMPLE_TICKS != 0) {
            return;
        }
        long today = DataCenterApi.currentDate(server.overworld()).toEpochDay();
        ReputationBook book = ReputationBook.get(server);
        for (Map.Entry<UUID, ReputationBook.Account> e : new ArrayList<>(book.accounts().entrySet())) {
            ReputationBook.Account a = e.getValue();
            if (Config.reputationModel() && !ReputationBook.NOBODY.equals(e.getKey())) {
                Renown.customers(server.overworld(), e.getKey(), a, today);
                // the class depends on the Ruf too, which changes without the points: let Data Center re-check it
                ((HostingReputationInvoker) (Object) HostingReputation.get(server.overworld(), e.getKey()))
                        .data_center_addon$adjust(server, 0.0);
            }
            if (a.day < 0) {
                a.day = today;
            } else if (today > a.day) {
                if (Config.reputationModel() && !ReputationBook.NOBODY.equals(e.getKey())) {
                    Renown.closeDay(server.overworld(), e.getKey(), a, a.today, today);
                    Sla.closeDay(server.overworld(), e.getKey(), a, a.today, a.day, today);
                    Renown.forget(server.overworld(), e.getKey(), a, today);
                    Reviews.day(server.overworld(), e.getKey(), a, today);
                }
                closeDay(a);
                a.day = today;
            }
        }
        book.setDirty();
    }

    /** Moves the recorded day into the window (days without service are left out). */
    private static void closeDay(ReputationBook.Account a) {
        while (!a.incidents.isEmpty() && a.day - a.incidents.peekFirst() >= Ruf.WINDOW_DAYS) {
            a.incidents.removeFirst();
        }
        if (a.today[Ruf.SERVED] + a.today[Ruf.DOWN] > 0 || a.today[Ruf.CRASHES] > 0) {
            a.days.addLast(a.today.clone());
            while (a.days.size() > Ruf.WINDOW_DAYS) {
                a.days.removeFirst();
            }
        }
        Arrays.fill(a.today, 0);
    }

    /**
     * The Ruf that counts for the market of one product: the account's Ruf, or with
     * {@code rufPerProduct} half of it and half the product's own Ruf (once measured).
     */
    public static double rufFor(MinecraftServer server, @Nullable UUID owner, MarketBook.Entry entry) {
        double ruf = ruf(server, owner);
        if (Config.rufPerProduct()) {
            double product = ServiceQuality.productRuf(entry);
            if (!Double.isNaN(product)) {
                return 0.5 * ruf + 0.5 * product;
            }
        }
        return ruf;
    }

    /** The account's Ruf, 0..1 (1 without any service yet). */
    public static double ruf(MinecraftServer server, @Nullable UUID owner) {
        return Ruf.figures(ReputationBook.get(server).account(owner)).ruf();
    }

    public static HostingSnapshot.Standing snapshot(ServerLevel level, UUID account) {
        ReputationBook.Account a = ReputationBook.get(level.getServer()).account(account);
        Ruf.Figures f = Ruf.figures(a);
        double[] sources = new double[Renown.Source.values().length];
        a.sources().forEach((s, v) -> sources[s.ordinal()] = v);
        return new HostingSnapshot.Standing(f.ruf(), f.measured(), f.uptime(), f.overload(), f.throttle(), f.crashes(), f.incidents(),
                f.days(),
                DataCenterApi.reputation(level, account), toList(sources), Renown.pending(a),
                a.lastReview == null ? List.of() : List.of(a.lastReview[0], a.lastReview[1], a.lastReview[2], a.lastReview[3],
                        a.lastReview[4], DataCenterApi.currentDate(level).toEpochDay()),
                Campaigns.snapshot(a, DataCenterApi.currentDate(level)), slaUptime(a),
                a.lastGainDay < 0 ? 0 : (int) Math.max(0, DataCenterApi.currentDate(level).toEpochDay() - a.lastGainDay),
                toList(Renown.recent(a, DataCenterApi.currentDate(level).toEpochDay())));
    }

    /** Uptime of the calendar month so far (finished days and today); -1 without service. */
    private static double slaUptime(ReputationBook.Account a) {
        double served = a.slaServed + a.today[Ruf.SERVED];
        double total = served + a.slaDown + a.today[Ruf.DOWN];
        return total <= 0 ? -1 : served / total;
    }

    private static List<Double> toList(double[] values) {
        List<Double> out = new ArrayList<>();
        for (double v : values) {
            out.add(v);
        }
        return List.copyOf(out);
    }
}
