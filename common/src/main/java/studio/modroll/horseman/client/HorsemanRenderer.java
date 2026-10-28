package studio.modroll.horseman.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import studio.modroll.horseman.entity.Horseman;
import studio.modroll.horseman.entity.HorsemanEntities;

/** GeckoLib renderer with a glowmask layer. Hides the held head while he is headless. */
public final class HorsemanRenderer extends GeoEntityRenderer<Horseman> {

    public HorsemanRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(HorsemanEntities.HORSEMAN_ID));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        shadowRadius = 0.7F;
    }

    @Override
    public void preRender(
            PoseStack poseStack,
            Horseman horseman,
            BakedGeoModel model,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            int colour) {
        model.getBone("held_head").ifPresent(bone -> bone.setHidden(horseman.isHeadless()));
        super.preRender(
                poseStack,
                horseman,
                model,
                bufferSource,
                buffer,
                isReRender,
                partialTick,
                packedLight,
                packedOverlay,
                colour);
    }

    /** The death animation handles the fall, so no vanilla topple. */
    @Override
    protected float getDeathMaxRotation(Horseman horseman) {
        return 0.0F;
    }

    /** Keeps the hurt flash but drops the red tint for the whole death animation. */
    @Override
    public int getPackedOverlay(Horseman horseman, float u, float partialTick) {
        return OverlayTexture.pack(
                OverlayTexture.u(u), OverlayTexture.v(horseman.hurtTime > 0 && !horseman.isDeadOrDying()));
    }
}
