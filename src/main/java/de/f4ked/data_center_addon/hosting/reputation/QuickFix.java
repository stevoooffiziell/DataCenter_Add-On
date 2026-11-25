package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.mining.MiningEra;
import com.example.datacenter.wallet.OwnerMessages;
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
 * Fixing a failure fast: when a rack that served customers has servers down and is back to full service
 * within {@link #MAX_HOURS} game hours, the account gets a little renown instead of only the Ruf it lost.
 * At most once per rack every {@link #COOLDOWN_DAYS} in-game days; very short blips (under
 * {@link #MIN_HOURS}) do not count.
 */
public final class QuickFix {
    static final double MAX_HOURS = 6, MIN_HOURS = 0.5;
    static final double RENOWN = 1.0;
    static final double COOLDOWN_DAYS = 3;

    /** Per rack: game hours since the failure began (NaN: none open), and game hours since the last bonus. */
    private static final Map<GlobalPos, double[]> RACKS = new HashMap<>();

    private QuickFix() {
    }

    /** One tick of a rack's service. */
    static void service(ServerLevel level, BlockPos pos, @Nullable UUID owner, double earning, double down) {
        double hours = MiningEra.fictionalHoursPerTick();
        double[] s = RACKS.computeIfAbsent(GlobalPos.of(level.dimension(), pos), p -> new double[]{Double.NaN, Double.MAX_VALUE});
        s[1] += hours;
        if (down > 0) {
            // a failure that lasts far too long is no quick fix any more
            s[0] = Double.isNaN(s[0]) ? 0 : s[0] > MAX_HOURS * 4 ? MAX_HOURS * 4 : s[0] + hours;
        } else if (!Double.isNaN(s[0])) {
            double took = s[0];
            s[0] = Double.NaN;
            if (earning > 0 && took >= MIN_HOURS && took <= MAX_HOURS && s[1] >= COOLDOWN_DAYS * 24) {
                s[1] = 0;
                Renown.add(level, owner, RENOWN, Renown.Source.QUICK_FIX);
                OwnerMessages.toOwner(level.getServer(), owner, Component.translatable("data_center_addon.reputation.msg.quick_fix",
                        pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), String.format(java.util.Locale.ROOT, "%.1f", took),
                        "+" + Math.round(RENOWN)).withStyle(ChatFormatting.GREEN));
            }
        }
    }

    static void clear() {
        RACKS.clear();
    }
}
