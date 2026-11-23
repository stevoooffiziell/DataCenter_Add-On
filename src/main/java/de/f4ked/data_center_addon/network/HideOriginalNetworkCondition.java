package de.f4ked.data_center_addon.network;

import com.mojang.serialization.MapCodec;
import de.f4ked.data_center_addon.Config;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Data-pack condition {@code data_center_addon:hide_original_network}: true when the config hides Data
 * Center's own cables and switches. The overrides of Data Center's cable recipes wrap it in
 * {@code neoforge:not}, so the recipes only load while the originals are shown.
 */
public final class HideOriginalNetworkCondition implements ICondition {
    public static final HideOriginalNetworkCondition INSTANCE = new HideOriginalNetworkCondition();
    public static final MapCodec<HideOriginalNetworkCondition> CODEC = MapCodec.unit(INSTANCE);

    private HideOriginalNetworkCondition() {
    }

    @Override
    public boolean test(IContext context) {
        return Config.hidesOriginalNetwork();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
