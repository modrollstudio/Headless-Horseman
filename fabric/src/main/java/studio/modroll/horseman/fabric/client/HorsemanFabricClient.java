package studio.modroll.horseman.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import studio.modroll.horseman.client.HorsemanRenderer;
import studio.modroll.horseman.client.SteedRenderer;
import studio.modroll.horseman.entity.HorsemanEntities;

public final class HorsemanFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(HorsemanEntities.HORSEMAN, HorsemanRenderer::new);
        EntityRendererRegistry.register(HorsemanEntities.STEED, SteedRenderer::new);
        EntityRendererRegistry.register(
                HorsemanEntities.THROWN_HEAD, context -> new ThrownItemRenderer<>(context, 1.6F, true));
    }
}
