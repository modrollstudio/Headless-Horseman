package studio.modroll.horseman.neoforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.client.Atmosphere;
import studio.modroll.horseman.client.GallowsRopesRenderer;
import studio.modroll.horseman.client.HeadModel;
import studio.modroll.horseman.client.HorsemanConfigScreen;
import studio.modroll.horseman.client.HorsemanRenderer;
import studio.modroll.horseman.client.LanternKeys;
import studio.modroll.horseman.client.SteedRenderer;
import studio.modroll.horseman.entity.HorsemanEntities;
import studio.modroll.horseman.item.HorsemanItems;

/**
 * Client-only setup: the client config, renderers, the lantern keys and the head's model property, fog, camera shake
 * and the mod list's config screen.
 */
public final class HorsemanNeoForgeClient {

    private HorsemanNeoForgeClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        CritfallHorseman.initClient();
        container.registerExtensionPoint(
                IConfigScreenFactory.class, (mod, parent) -> HorsemanConfigScreen.forCurrentGame(parent));
        modBus.addListener(HorsemanNeoForgeClient::onRegisterRenderers);
        modBus.addListener(HorsemanNeoForgeClient::onRegisterKeys);
        modBus.addListener(HorsemanNeoForgeClient::onClientSetup);
        NeoForge.EVENT_BUS.addListener(HorsemanNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(HorsemanNeoForgeClient::onRenderFog);
        NeoForge.EVENT_BUS.addListener(HorsemanNeoForgeClient::onCameraAngles);
    }

    /** The event has to be cancelled for the new distances to apply. */
    private static void onRenderFog(ViewportEvent.RenderFog event) {
        float partialTick = (float) event.getPartialTick();
        if (Atmosphere.foggy(partialTick)) {
            event.setNearPlaneDistance(Atmosphere.fogStart(event.getNearPlaneDistance(), partialTick));
            event.setFarPlaneDistance(Atmosphere.fogEnd(event.getFarPlaneDistance(), partialTick));
            event.setCanceled(true);
        }
    }

    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (Atmosphere.shake() > 0.0F) {
            float partialTick = (float) event.getPartialTick();
            event.setRoll(event.getRoll() + Atmosphere.shakeRoll(partialTick));
            event.setPitch(event.getPitch() + Atmosphere.shakePitch(partialTick));
        }
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(LanternKeys.LIGHT);
        event.register(LanternKeys.BREATH);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(
                () -> ItemProperties.register(HorsemanItems.HORSEMAN_HEAD, HeadModel.UNLIT, HeadModel::unlit));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        LanternKeys.tick(Minecraft.getInstance(), PacketDistributor::sendToServer);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HorsemanEntities.HORSEMAN, HorsemanRenderer::new);
        event.registerEntityRenderer(HorsemanEntities.STEED, SteedRenderer::new);
        event.registerEntityRenderer(
                HorsemanEntities.THROWN_HEAD, context -> new ThrownItemRenderer<>(context, 1.6F, true));
        event.registerEntityRenderer(HorsemanEntities.GALLOWS_ROPES, GallowsRopesRenderer::new);
    }
}
