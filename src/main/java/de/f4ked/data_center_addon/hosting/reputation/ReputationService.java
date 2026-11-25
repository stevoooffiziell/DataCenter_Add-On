package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.api.DataCenterApi;
import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.hosting.HostingSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
 * {@link Ruf} of the last 30 days.
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

    /** A rack of the account crashed. */
    public static void crash(MinecraftServer server, @Nullable UUID owner) {
        ReputationBook book = ReputationBook.get(server);
        book.account(owner).today[Ruf.CRASHES]++;
        book.setDirty();
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
            if (Config.reputationModel()) {
                Renown.customers(server.overworld(), e.getKey(), a);
            }
            if (a.day < 0) {
                a.day = today;
            } else if (today > a.day) {
                closeDay(a);
                a.day = today;
            }
        }
        book.setDirty();
    }

    /** Moves the recorded day into the window (days without service are left out). */
    private static void closeDay(ReputationBook.Account a) {
        if (a.today[Ruf.SERVED] + a.today[Ruf.DOWN] > 0 || a.today[Ruf.CRASHES] > 0) {
            a.days.addLast(a.today.clone());
            while (a.days.size() > Ruf.WINDOW_DAYS) {
                a.days.removeFirst();
            }
        }
        Arrays.fill(a.today, 0);
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
        return new HostingSnapshot.Standing(f.ruf(), f.measured(), f.uptime(), f.overload(), f.throttle(), f.crashes(), f.days(),
                DataCenterApi.reputation(level, account), toList(sources));
    }

    private static List<Double> toList(double[] values) {
        List<Double> out = new ArrayList<>();
        for (double v : values) {
            out.add(v);
        }
        return List.copyOf(out);
    }
}
