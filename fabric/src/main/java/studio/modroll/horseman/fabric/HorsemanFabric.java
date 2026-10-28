package studio.modroll.horseman.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.entity.HorsemanEntities;
import studio.modroll.horseman.item.HorsemanItems;
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
        ServerTickEvents.END_WORLD_TICK.register(HorsemanSpawner::tick);
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
