package studio.modroll.horseman.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.client.GallowsRopesRenderer;
import studio.modroll.horseman.client.HeadModel;
import studio.modroll.horseman.client.HorsemanRenderer;
import studio.modroll.horseman.client.LanternKeys;
import studio.modroll.horseman.client.SteedRenderer;
import studio.modroll.horseman.entity.HorsemanEntities;
import studio.modroll.horseman.item.HorsemanItems;

public final class HorsemanFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CritfallHorseman.initClient();
        EntityRendererRegistry.register(HorsemanEntities.HORSEMAN, HorsemanRenderer::new);
        EntityRendererRegistry.register(HorsemanEntities.STEED, SteedRenderer::new);
        EntityRendererRegistry.register(
                HorsemanEntities.THROWN_HEAD, context -> new ThrownItemRenderer<>(context, 1.6F, true));
        EntityRendererRegistry.register(HorsemanEntities.GALLOWS_ROPES, GallowsRopesRenderer::new);
        KeyBindingHelper.registerKeyBinding(LanternKeys.LIGHT);
        KeyBindingHelper.registerKeyBinding(LanternKeys.BREATH);
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> LanternKeys.tick(minecraft, ClientPlayNetworking::send));
        ItemProperties.register(HorsemanItems.HORSEMAN_HEAD, HeadModel.UNLIT, HeadModel::unlit);
    }
}
