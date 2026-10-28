package studio.modroll.horseman.fabric.uishots;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import studio.modroll.horseman.uishots.UiShots;

/** Dev-only; only the runUiShots task puts this source set on the classpath. */
public final class FabricUiShots implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> UiShots.tick());
    }
}
