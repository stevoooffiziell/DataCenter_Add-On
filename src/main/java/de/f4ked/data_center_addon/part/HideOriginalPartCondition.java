package de.f4ked.data_center_addon.part;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.f4ked.data_center_addon.Config;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Data-pack condition {@code data_center_addon:hide_original}: true when the config hides the original
 * Data Center parts of the given shop category. Used by the generated
 * {@code data/datacenter/datacenter/part/*.json} overrides that set {@code "hidden": true}.
 */
public record HideOriginalPartCondition(String category) implements ICondition {
    public static final MapCodec<HideOriginalPartCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("category").forGetter(HideOriginalPartCondition::category)
    ).apply(instance, HideOriginalPartCondition::new));

    @Override
    public boolean test(IContext context) {
        return Config.hidesOriginal(category);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
