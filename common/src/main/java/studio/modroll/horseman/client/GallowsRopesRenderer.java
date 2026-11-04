package studio.modroll.horseman.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import studio.modroll.horseman.entity.GallowsRopes;
import studio.modroll.horseman.entity.HorsemanEntities;

/** GeckoLib renderer for the ropes, with the glowing cracks in their earth. */
public final class GallowsRopesRenderer extends GeoEntityRenderer<GallowsRopes> {

    public GallowsRopesRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(HorsemanEntities.GALLOWS_ROPES_ID));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        shadowRadius = 0.0F;
    }
}
