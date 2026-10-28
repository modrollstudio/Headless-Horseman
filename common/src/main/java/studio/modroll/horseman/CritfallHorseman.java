package studio.modroll.horseman;

import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.modroll.horseman.config.HorsemanClientConfig;
import studio.modroll.horseman.config.HorsemanConfig;
import studio.modroll.horseman.config.HorsemanConfigLoader;

/** Loader-agnostic entrypoint. Each loader module calls {@link #init} once at mod construction. */
public final class CritfallHorseman {

    public static final String MOD_ID = "critfall_horseman";
    public static final String MOD_NAME = "Critfall: Headless Horseman";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    // The config screen replaces these on the render thread while the integrated server reads them.
    private static volatile HorsemanConfig config = HorsemanConfig.DEFAULTS;
    private static volatile HorsemanClientConfig clientConfig = HorsemanClientConfig.DEFAULTS;
    private static Path configDir;

    private CritfallHorseman() {}

    /** {@code configDir} is the loader's config folder. */
    public static void init(Path configDir) {
        CritfallHorseman.configDir = configDir;
        config = HorsemanConfigLoader.load(configFile());
        LOG.info("{} initialized{}", MOD_NAME, config.enabled() ? "" : " (switched off in config)");
    }

    /** Called by each loader's client setup, after {@link #init}. */
    public static void initClient() {
        clientConfig = HorsemanConfigLoader.loadClient(clientConfigFile());
    }

    /** Reads both files again; the config screen calls this after saving. */
    public static void reloadConfig() {
        config = HorsemanConfigLoader.load(configFile());
        clientConfig = HorsemanConfigLoader.loadClient(clientConfigFile());
    }

    public static Path configFile() {
        return configDir.resolve(MOD_ID).resolve(MOD_ID + ".json");
    }

    public static Path clientConfigFile() {
        return configDir.resolve(MOD_ID).resolve(MOD_ID + "-client.json");
    }

    public static HorsemanConfig config() {
        return config;
    }

    public static HorsemanClientConfig clientConfig() {
        return clientConfig;
    }

    /** For tests: replaces the loaded config. */
    public static void useConfig(HorsemanConfig config) {
        CritfallHorseman.config = config;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
