package studio.modroll.horseman.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import studio.modroll.horseman.entity.GallowsSteed;
import studio.modroll.horseman.entity.HorsemanEntities;

/** GeckoLib renderer with a glowmask layer. */
public final class SteedRenderer extends GeoEntityRenderer<GallowsSteed> {

    public SteedRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(HorsemanEntities.STEED_ID));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        shadowRadius = 1.0F;
    }

    @Override
    protected float getDeathMaxRotation(GallowsSteed steed) {
        return 0.0F;
    }

    /** Keeps the hurt flash but drops the red tint for the whole death animation. */
    @Override
    public int getPackedOverlay(GallowsSteed steed, float u, float partialTick) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(steed.hurtTime > 0 && !steed.isDeadOrDying()));
    }
}
