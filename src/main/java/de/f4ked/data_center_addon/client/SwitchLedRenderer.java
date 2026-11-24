package de.f4ked.data_center_addon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.f4ked.data_center_addon.Data_center_addon;
import de.f4ked.data_center_addon.network.NetworkContent;
import de.f4ked.data_center_addon.network.NetworkSwitchBlock;
import de.f4ked.data_center_addon.network.NetworkSwitchBlockEntity;
import de.f4ked.data_center_addon.network.SwitchModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;

/**
 * Lights the LEDs on a switch's front, over the LED dots painted on its texture:
 * <ul>
 *   <li>Port LEDs: steady green with a link and no traffic, flickering the busier the network is,
 *       amber when the network runs at its limit, dark without power.</li>
 *   <li>Status LED (bottom right): green, amber at the limit, blinking red without power.</li>
 * </ul>
 * Drawn full bright, so a server room shows at a glance which switches carry traffic and which are dark.
 */
@EventBusSubscriber(modid = Data_center_addon.MODID, value = Dist.CLIENT)
public class SwitchLedRenderer implements BlockEntityRenderer<NetworkSwitchBlockEntity> {
    private static final int GREEN = 0xFF3CFF6E;
    private static final int AMBER = 0xFFFFB020;
    private static final int RED = 0xFFFF3030;
    /** Left edges of the port LEDs in pixels (1/16 block): the dots painted on the front textures. */
    private static final float[] PORT_X = {1.5f, 2.5f, 3.5f, 4.5f, 6.5f, 7.5f, 8.5f, 10.5f};
    private static final float LED = 0.5f;
    /** The front face of Data Center's switch model, facing north before rotation. */
    private static final float FRONT_Z = 2f / 16f - 0.002f;
    /** Chance a port LED is dark in a flicker step, by activity 0..4. */
    private static final float[] FLICKER = {0f, 0.15f, 0.35f, 0.55f, 0.7f};
    private static final int FLICKER_TICKS = 2;

    public SwitchLedRenderer(BlockEntityRendererProvider.Context context) {
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(NetworkContent.SWITCH_ENTITY.get(), SwitchLedRenderer::new);
    }

    @Override
    public void render(NetworkSwitchBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (be.getLevel() == null || !(be.getBlockState().getBlock() instanceof NetworkSwitchBlock block)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(NetworkSwitchBlock.FACING);
        long time = be.getLevel().getGameTime();
        boolean powered = be.isPowered();
        int activity = be.activity();
        boolean saturated = activity >= NetworkSwitchBlockEntity.ACTIVITY_SATURATED;

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer buffer = buffers.getBuffer(RenderType.debugQuads());

        float rowY = twoRows(block.model()) ? 1.5f : 1.0f;
        if (powered) {
            long seed = be.getBlockPos().asLong();
            for (int i = 0; i < PORT_X.length; i++) {
                long step = (time + i * 7L) / FLICKER_TICKS;
                float roll = (mix(seed * 31 + i * 7919L + step * 104729L) & 0xFFFF) / 65536f;
                if (roll >= FLICKER[Math.min(activity, FLICKER.length - 1)]) {
                    quad(buffer, matrix, PORT_X[i], rowY, LED, LED, saturated ? AMBER : GREEN);
                }
            }
            quad(buffer, matrix, 14.5f, 0f, 0.5f, 1f, saturated ? AMBER : GREEN);
        } else if ((time / 10) % 2 == 0) {
            quad(buffer, matrix, 14.5f, 0f, 0.5f, 1f, RED);
        }
        pose.popPose();
    }

    /** 64-bit finaliser of MurmurHash3: a cheap, well spread pseudo-random value per LED and step. */
    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        return h ^ (h >>> 33);
    }

    /** Switches whose front has two port rows; their lower LED row sits one texel higher. */
    private static boolean twoRows(SwitchModel model) {
        return model == SwitchModel.ARISTA_7050S_64 || model == SwitchModel.ARISTA_7060CX_32S
                || model == SwitchModel.NVIDIA_SPECTRUM_SN4700;
    }

    /** A flat rectangle on the front face; x and y in pixels from the bottom left seen from the front. */
    private static void quad(VertexConsumer buffer, Matrix4f matrix, float x, float y, float w, float h, int argb) {
        // seen from the north the block's x axis runs right to left
        float x0 = 1f - (x + w) / 16f, x1 = 1f - x / 16f;
        float y0 = y / 16f, y1 = (y + h) / 16f;
        buffer.addVertex(matrix, x0, y0, FRONT_Z).setColor(argb);
        buffer.addVertex(matrix, x0, y1, FRONT_Z).setColor(argb);
        buffer.addVertex(matrix, x1, y1, FRONT_Z).setColor(argb);
        buffer.addVertex(matrix, x1, y0, FRONT_Z).setColor(argb);
    }
}
