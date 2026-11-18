package studio.modroll.horseman.fabric.demo;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import studio.modroll.horseman.demo.HorsemanDemo;

/** Dev-only; only the client, client2 and server runs put this source set on the classpath. */
public final class FabricDemo implements ModInitializer {

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, selection) -> HorsemanDemo.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(HorsemanDemo::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> HorsemanDemo.stop());
    }
}
