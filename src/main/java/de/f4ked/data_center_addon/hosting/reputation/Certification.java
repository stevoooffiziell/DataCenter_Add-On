package de.f4ked.data_center_addon.hosting.reputation;

import com.example.datacenter.certification.CertificationTier;
import com.example.datacenter.wallet.OwnerMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Certifications: the first time a serving rack of the account stands in a room of a higher tier, the
 * account gets a large one-time boost of renown ({@link #RENOWN}, by tier). The tier an account already
 * had when the add-on first saw it counts as reached, without a boost.
 */
public final class Certification {
    /** Renown for reaching each tier, by {@link CertificationTier#level} (none, I, II, III, IV). */
    static final int[] RENOWN = {0, 25, 50, 100, 150};

    private Certification() {
    }

    /** A serving rack of the account stands in a room of this certification level. */
    static void seen(ServerLevel level, @Nullable UUID owner, int tier) {
        ClassRules.roomSeen(level.getServer(), owner, tier);
        ReputationBook book = ReputationBook.get(level.getServer());
        ReputationBook.Account a = book.account(owner);
        if (a.maxTier < 0) {
            a.maxTier = tier;
            book.setDirty();
            return;
        }
        if (tier <= a.maxTier) {
            return;
        }
        int renown = 0;
        for (int t = a.maxTier + 1; t <= tier && t < RENOWN.length; t++) {
            renown += RENOWN[t];
        }
        a.maxTier = tier;
        book.setDirty();
        if (renown > 0) {
            Renown.add(level, owner, renown, Renown.Source.CERTIFICATION);
            OwnerMessages.toOwner(level.getServer(), owner, Component.translatable("data_center_addon.reputation.msg.certification",
                    Component.translatable(CertificationTier.ofLevel(tier).translationKey()), "+" + renown)
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}
