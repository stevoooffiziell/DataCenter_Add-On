package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.mining.MiningEra;
import com.example.datacenter.wallet.OwnerMessages;
import de.f4ked.data_center_addon.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Notices when the reputation suffers: a chat message names the rack and what costs the Ruf, e.g.
 * "−8 % Ruf: rack at 120, 64, −30 crashed". Downtime and throttling are reported when they begin, at most
 * once per rack and kind every {@link #REPEAT_HOURS} game hours.
 */
public final class LossNotices {
    static final double REPEAT_HOURS = 24;

    /** Per rack: was down, was throttled, game hours since the last downtime / throttling notice. */
    private static final Map<GlobalPos, double[]> RACKS = new HashMap<>();

    private LossNotices() {
    }

    /** One tick of a rack's service. */
    static void service(ServerLevel level, BlockPos pos, @Nullable UUID owner, double earning, boolean throttled, double down) {
        if (!Config.lossNotices()) {
            return;
        }
        double hours = MiningEra.fictionalHoursPerTick();
        double[] s = RACKS.computeIfAbsent(GlobalPos.of(level.dimension(), pos), p -> new double[]{0, 0, 1e9, 1e9});
        s[2] += hours;
        s[3] += hours;
        boolean isDown = down > 0 && earning + down > 0;
        boolean isThrottled = throttled && earning > 0;
        if (isDown && s[0] == 0 && s[2] >= REPEAT_HOURS) {
            s[2] = 0;
            notice(level, owner, Component.translatable("data_center_addon.reputation.msg.loss_down", where(pos)));
        }
        if (isThrottled && s[1] == 0 && s[3] >= REPEAT_HOURS) {
            s[3] = 0;
            notice(level, owner, Component.translatable("data_center_addon.reputation.msg.loss_throttled", where(pos)));
        }
        s[0] = isDown ? 1 : 0;
        s[1] = isThrottled ? 1 : 0;
    }

    /** A rack crashed: a serious incident ({@link Ruf#INCIDENT}). */
    public static void crash(ServerLevel level, BlockPos pos, @Nullable UUID owner) {
        if (Config.lossNotices()) {
            notice(level, owner, Component.translatable("data_center_addon.reputation.msg.loss_crash",
                    "−" + Math.round(Ruf.INCIDENT * 100) + " %", where(pos)));
        }
    }

    private static void notice(ServerLevel level, @Nullable UUID owner, Component text) {
        OwnerMessages.toOwner(level.getServer(), owner, text.copy().withStyle(ChatFormatting.GOLD));
    }

    private static String where(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    static void clear() {
        RACKS.clear();
    }
}
