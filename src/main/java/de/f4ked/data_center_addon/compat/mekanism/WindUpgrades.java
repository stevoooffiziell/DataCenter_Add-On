package de.f4ked.data_center_addon.compat.mekanism;

import de.f4ked.data_center_addon.Config;
import de.f4ked.data_center_addon.mixin.mekanism.TileEntityGeneratorInvoker;
import mekanism.api.Upgrade;
import mekanism.api.math.MathUtils;
import mekanism.common.block.attribute.AttributeUpgradeSupport;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.generators.common.config.MekanismGeneratorsConfig;
import mekanism.generators.common.registries.GeneratorsBlockTypes;
import mekanism.generators.common.tile.TileEntityWindGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Speed Upgrades for Mekanism's Wind Generator, like in Mekanism's machines (e.g. the Metallurgic Infuser):
 * <ul>
 *   <li>The Wind Generator takes Speed Upgrades besides its Muffling Upgrades, at most
 *       {@link Config#windSpeedUpgrades()} of them (Mekanism's machines take 8).</li>
 *   <li>Each one raises the generation as Speed Upgrades raise a machine's speed:
 *       {@code × maxUpgradeMultiplier^(upgrades / 8)}, the effect the upgrade window shows.</li>
 *   <li>The most it hands on per tick grows by the same factor, so the extra energy can leave.</li>
 * </ul>
 * Only loaded with Mekanism Generators installed; the hooks are in {@code mixin.mekanism}.
 */
public final class WindUpgrades {
    private WindUpgrades() {
    }

    /** Called while the add-on is constructed, only with Mekanism Generators installed. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(WindUpgrades::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (Config.windSpeedUpgrades() > 0) {
                // replaces Mekanism's MUFFLING_ONLY; the block's tiles read it when they are created
                GeneratorsBlockTypes.WIND_GENERATOR.add(AttributeUpgradeSupport.create(Upgrade.SPEED, Upgrade.MUFFLING));
            }
        });
    }

    /** The most upgrades of a kind the tile takes: fewer Speed Upgrades for the Wind Generator. */
    public static int max(TileEntityMekanism tile, Upgrade upgrade) {
        if (upgrade == Upgrade.SPEED && tile instanceof TileEntityWindGenerator) {
            return Math.min(upgrade.getMax(), Config.windSpeedUpgrades());
        }
        return upgrade.getMax();
    }

    /** The factor the installed Speed Upgrades raise the generation by (1 without any). */
    public static double factor(TileEntityMekanism tile) {
        TileComponentUpgrade component = tile.getComponent();
        if (component == null || !component.supports(Upgrade.SPEED)) {
            return 1.0;
        }
        int speed = component.getUpgrades(Upgrade.SPEED);
        if (speed <= 0) {
            return 1.0;
        }
        return Math.pow(MekanismConfig.general.maxUpgradeMultiplier.get(), speed / (double) Upgrade.SPEED.getMax());
    }

    /** The generation with the Speed Upgrades. */
    public static long generation(TileEntityWindGenerator tile, long base) {
        double factor = factor(tile);
        return factor == 1.0 ? base : MathUtils.clampToLong(base * factor);
    }

    /** Raises the generator's output limit (twice the best generation) by the factor of its Speed Upgrades. */
    public static void updateMaxOutput(TileEntityWindGenerator tile) {
        long max = MekanismGeneratorsConfig.generators.windGenerationMax.get();
        ((TileEntityGeneratorInvoker) tile).data_center_addon$updateMaxOutputRaw(MathUtils.clampToLong(max * factor(tile)));
    }
}
