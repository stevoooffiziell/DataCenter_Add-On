package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.hosting.HostingReputation;
import com.example.datacenter.mining.MiningEra;
import de.f4ked.data_center_addon.Config;
import net.minecraft.server.MinecraftServer;

/**
 * What a hosting rack's running service does to the reputation. Every rack reports its service once a
 * tick to Data Center's reputation ({@code recordService}); the add-on takes that call
 * ({@code HostingRackBlockEntityMixin}).
 * <p>
 * Data Center adds 0.05 points per serving server and game hour (more with a certified room) and takes
 * points for downtime, throttling and overload. With the add-on's reputation model on
 * ({@link Config#reputationModel()}) running servers no longer earn points; the deductions stay.
 */
public final class RackService {
    /** Data Center's points per server and game hour: throttled, overloaded, down. */
    static final double THROTTLE_PER_HOUR = 0.05, OVERLOAD_PER_HOUR = 0.05, DOWN_PER_HOUR = 0.025;

    private RackService() {
    }

    /**
     * A rack's service of one tick, in place of {@link HostingReputation#recordService}.
     *
     * @param earning    servers (or colocation customers) that served
     * @param throttled  the rack ran throttled (heat, attack, slow network)
     * @param down       servers (or customers) that did not run
     * @param overloaded how far the servers ran over their real capacity
     */
    public static void service(HostingReputation reputation, MinecraftServer server, double earning, boolean throttled,
                               int certification, double down, double overloaded) {
        if (!Config.reputationModel()) {
            reputation.recordService(server, earning, throttled, certification, down, overloaded);
            return;
        }
        double hours = MiningEra.fictionalHoursPerTick();
        double delta = 0;
        if (earning > 0) {
            delta -= throttled
                    ? THROTTLE_PER_HOUR * earning * hours
                    : OVERLOAD_PER_HOUR * Math.min(earning, Math.max(0, overloaded)) * hours;
        }
        delta -= DOWN_PER_HOUR * Math.max(0, down) * hours;
        if (delta != 0) {
            reputation.change(server, delta);
        }
    }
}
