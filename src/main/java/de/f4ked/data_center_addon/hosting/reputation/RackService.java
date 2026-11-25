package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.hosting.HostingReputation;
import de.f4ked.data_center_addon.Config;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What a hosting rack's running service does to the reputation. Every rack reports its service once a
 * tick to Data Center's reputation ({@code recordService}); the add-on takes that call
 * ({@code HostingRackBlockEntityMixin}).
 * <p>
 * Data Center adds 0.05 points per serving server and game hour (more with a certified room) and takes
 * points for downtime, throttling and overload. With the add-on's reputation model on
 * ({@link Config#reputationModel()}) running is the baseline, not a source: the service no longer moves
 * the points at all. It goes into the {@link Ruf} instead, a sliding value of the last 30 days.
 */
public final class RackService {
    private RackService() {
    }

    /**
     * A rack's service of one tick, in place of {@link HostingReputation#recordService}.
     *
     * @param owner      the rack's owner (account)
     * @param earning    servers (or colocation customers) that served
     * @param throttled  the rack ran throttled (heat, attack, slow network)
     * @param down       servers (or customers) that did not run
     * @param overloaded how far the servers ran over their real capacity
     */
    public static void service(HostingReputation reputation, MinecraftServer server, @Nullable UUID owner, double earning,
                               boolean throttled, int certification, double down, double overloaded) {
        if (!Config.reputationModel()) {
            reputation.recordService(server, earning, throttled, certification, down, overloaded);
            return;
        }
        ReputationService.record(server, owner, earning, down, overloaded, throttled);
    }
}
