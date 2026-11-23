package de.f4ked.data_center_addon.client;

import com.example.datacenter.server.ServerItem;
import com.mojang.datafixers.util.Either;
import de.f4ked.data_center_addon.Data_center_addon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Rarity-style tooltips for Data Center parts and servers: a border and title divider in the tier
 * colour, a tier line under the name, and for servers a list of the built-in parts.
 */
@EventBusSubscriber(modid = Data_center_addon.MODID, value = Dist.CLIENT)
public final class TieredTooltips {
    private static final int VANILLA_BACKGROUND = 0x100010;

    private TieredTooltips() {
    }

    @SubscribeEvent
    static void onRegisterFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ServerPartsTooltip.class, tooltip -> tooltip);
        event.register(TierDivider.class, divider -> divider);
    }

    @SubscribeEvent
    static void onGather(RenderTooltipEvent.GatherComponents event) {
        if (!ClientConfig.SPEC.isLoaded()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        if (stack.isEmpty() || elements.isEmpty()) {
            return;
        }
        PartTier tier;
        if (stack.getItem() instanceof ServerItem) {
            ServerParts parts = ServerParts.of(stack);
            if (ClientConfig.SERVER_COMPONENTS.get()) {
                elements.add(Either.right(new ServerPartsTooltip(parts)));
            }
            tier = parts.tier();
        } else {
            tier = PartTier.of(stack);
        }
        if (tier == null || !ClientConfig.TIERED_TOOLTIPS.get()) {
            return;
        }
        elements.add(1, Either.left(Component.literal("◆ ").append(tier.displayName()).withColor(tier.text)));
        int width = estimateWidth(elements, Minecraft.getInstance().font);
        if (event.getMaxWidth() > 0) {
            width = Math.min(width, event.getMaxWidth());
        }
        elements.add(2, Either.right(new TierDivider(tier, width)));
    }

    @SubscribeEvent
    static void onColor(RenderTooltipEvent.Color event) {
        if (!ClientConfig.SPEC.isLoaded() || !ClientConfig.TIERED_TOOLTIPS.get()) {
            return;
        }
        PartTier tier = tierOf(event.getItemStack());
        if (tier == null) {
            return;
        }
        event.setBorderStart(tier.text & 0x00FFFFFF | 0xD0000000);
        event.setBorderEnd(tier.border & 0x00FFFFFF | 0x90000000);
        event.setBackgroundStart(0xF0000000 | mix(VANILLA_BACKGROUND, tier.border, 0.14f));
        event.setBackgroundEnd(0xF0000000 | mix(VANILLA_BACKGROUND, tier.border, 0.04f));
    }

    @Nullable
    private static PartTier tierOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return stack.getItem() instanceof ServerItem ? ServerParts.of(stack).tier() : PartTier.of(stack);
    }

    private static int estimateWidth(List<Either<FormattedText, TooltipComponent>> elements, Font font) {
        int width = 0;
        for (Either<FormattedText, TooltipComponent> element : elements) {
            width = Math.max(width, element.map(font::width, component -> {
                try {
                    return ClientTooltipComponent.create(component).getWidth(font);
                } catch (RuntimeException e) {
                    return 0;
                }
            }));
        }
        return width;
    }

    private static int mix(int base, int tint, float amount) {
        int r = Math.round(((base >> 16) & 0xFF) * (1 - amount) + ((tint >> 16) & 0xFF) * amount);
        int g = Math.round(((base >> 8) & 0xFF) * (1 - amount) + ((tint >> 8) & 0xFF) * amount);
        int b = Math.round((base & 0xFF) * (1 - amount) + (tint & 0xFF) * amount);
        return r << 16 | g << 8 | b;
    }
}
