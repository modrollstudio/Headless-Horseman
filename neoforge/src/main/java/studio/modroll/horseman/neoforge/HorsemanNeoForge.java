package studio.modroll.horseman.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.entity.HorsemanEntities;
import studio.modroll.horseman.item.HorsemanItems;
import studio.modroll.horseman.neoforge.client.HorsemanNeoForgeClient;
import studio.modroll.horseman.sound.HorsemanSounds;
import studio.modroll.horseman.spawn.HorsemanSpawner;

@Mod(CritfallHorseman.MOD_ID)
public final class HorsemanNeoForge {

    public HorsemanNeoForge(IEventBus modBus, ModContainer container) {
        CritfallHorseman.init(FMLPaths.CONFIGDIR.get());
        modBus.addListener(HorsemanNeoForge::onRegister);
        modBus.addListener(HorsemanNeoForge::onAttributes);
        NeoForge.EVENT_BUS.addListener(HorsemanNeoForge::onLevelTick);
        if (FMLEnvironment.dist.isClient()) {
            HorsemanNeoForgeClient.init(modBus, container);
        }
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> HorsemanEntities.register(helper::register));
        event.register(Registries.ITEM, helper -> HorsemanItems.register(helper::register));
        event.register(Registries.SOUND_EVENT, helper -> HorsemanSounds.register(helper::register));
        event.register(
                Registries.CREATIVE_MODE_TAB,
                helper -> helper.register(
                        HorsemanItems.TAB,
                        CreativeModeTab.builder()
                                .title(HorsemanItems.tabTitle())
                                .icon(HorsemanItems::tabIcon)
                                .displayItems(HorsemanItems::fillTab)
                                .build()));
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            HorsemanSpawner.tick(level);
        }
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        HorsemanEntities.attributes(event::put);
    }
}
