package studio.modroll.horseman.neoforge.demo;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.demo.HorsemanDemo;

/** Dev-only; the demo source set is never in the release jar. */
@EventBusSubscriber(modid = CritfallHorseman.MOD_ID)
public final class NeoForgeDemo {

    private NeoForgeDemo() {}

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        HorsemanDemo.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        HorsemanDemo.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        HorsemanDemo.stop();
    }
}
