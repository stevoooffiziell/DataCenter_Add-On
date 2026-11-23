package de.f4ked.data_center_addon.client;

import com.example.datacenter.catalog.PartPacks;
import de.f4ked.data_center_addon.Data_center_addon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Icons for the add-on's parts.
 * <p>
 * Data Center renders every data-pack part with the single model of its generic {@code datacenter:part}
 * item. This wraps that model: for a part of this add-on it shows
 * {@code data_center_addon:item/part/<part id>} instead (generated, re-using Data Center's own
 * textures); every other stack renders exactly as before.
 */
@EventBusSubscriber(modid = Data_center_addon.MODID, value = Dist.CLIENT)
public final class PartModels {
    private static final String MODEL_DIR = "models/item/part/";
    private static final String MODEL_PREFIX = "item/part/";
    private static final ModelResourceLocation DATA_CENTER_PART =
            ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath("datacenter", "part"));

    private PartModels() {
    }

    @SubscribeEvent
    static void registerModels(ModelEvent.RegisterAdditional event) {
        Minecraft.getInstance().getResourceManager()
                .listResources("models/item/part", file -> file.getPath().endsWith(".json"))
                .keySet().stream()
                .filter(file -> Data_center_addon.MODID.equals(file.getNamespace()))
                .forEach(file -> {
                    String path = file.getPath();
                    String name = path.substring(MODEL_DIR.length(), path.length() - ".json".length());
                    event.register(ModelResourceLocation.standalone(
                            ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID, MODEL_PREFIX + name)));
                });
    }

    @SubscribeEvent
    static void wrapPartModel(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        Map<ResourceLocation, BakedModel> byPart = new HashMap<>();
        models.forEach((location, model) -> {
            ResourceLocation id = location.id();
            if (ModelResourceLocation.STANDALONE_VARIANT.equals(location.variant())
                    && Data_center_addon.MODID.equals(id.getNamespace())
                    && id.getPath().startsWith(MODEL_PREFIX)) {
                byPart.put(ResourceLocation.fromNamespaceAndPath(Data_center_addon.MODID,
                        id.getPath().substring(MODEL_PREFIX.length())), model);
            }
        });
        BakedModel original = models.get(DATA_CENTER_PART);
        if (original != null && !byPart.isEmpty()) {
            models.put(DATA_CENTER_PART, new PartModel(original, byPart));
        }
    }

    private static final class PartModel extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;

        PartModel(BakedModel original, Map<ResourceLocation, BakedModel> byPart) {
            super(original);
            this.overrides = new ItemOverrides() {
                @Nullable
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                    ResourceLocation partId = PartPacks.partId(stack);
                    BakedModel own = partId == null ? null : byPart.get(partId);
                    if (own != null) {
                        return own;
                    }
                    return original.getOverrides().resolve(original, stack, level, entity, seed);
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }
}
