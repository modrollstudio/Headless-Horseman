package studio.modroll.horseman.fabric.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import studio.modroll.horseman.client.HorsemanConfigScreen;

/** Mod Menu's mod list opens the config screen. Only loaded when Mod Menu is installed. */
public final class HorsemanModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return HorsemanConfigScreen::forCurrentGame;
    }
}
