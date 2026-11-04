package studio.modroll.horseman.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.effect.HorsemanEffects;
import studio.modroll.horseman.entity.HorsemanEntities;
import studio.modroll.horseman.item.HorsemanItems;
import studio.modroll.horseman.network.LanternPayload;
import studio.modroll.horseman.reward.Rewards;
import studio.modroll.horseman.sound.HorsemanSounds;
import studio.modroll.horseman.spawn.HorsemanSpawner;

public final class HorsemanFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        CritfallHorseman.init(FabricLoader.getInstance().getConfigDir());

        HorsemanEntities.register((id, type) -> Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type));
        HorsemanEntities.attributes(FabricDefaultAttributeRegistry::register);
        HorsemanItems.register((id, item) -> Registry.register(BuiltInRegistries.ITEM, id, item));
        HorsemanSounds.register((id, sound) -> Registry.register(BuiltInRegistries.SOUND_EVENT, id, sound));
        Registry.register(BuiltInRegistries.MOB_EFFECT, HorsemanEffects.BOUND_ID, HorsemanEffects.BOUND);
        Registry.register(BuiltInRegistries.MOB_EFFECT, HorsemanEffects.WRAITHWALK_ID, HorsemanEffects.WRAITHWALK);
        ServerTickEvents.END_WORLD_TICK.register(HorsemanSpawner::tick);
        ServerTickEvents.END_WORLD_TICK.register(Rewards::tick);
        PayloadTypeRegistry.playC2S().register(LanternPayload.TYPE, LanternPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                LanternPayload.TYPE, (payload, context) -> LanternPayload.handle(payload, context.player()));
        Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                HorsemanItems.TAB,
                FabricItemGroup.builder()
                        .title(HorsemanItems.tabTitle())
                        .icon(HorsemanItems::tabIcon)
                        .displayItems(HorsemanItems::fillTab)
                        .build());
    }
}
