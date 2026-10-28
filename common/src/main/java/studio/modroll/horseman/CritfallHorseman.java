package studio.modroll.horseman;

import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.modroll.horseman.config.HorsemanConfig;
import studio.modroll.horseman.config.HorsemanConfigLoader;

/** Loader-agnostic entrypoint. Each loader module calls {@link #init} once at mod construction. */
public final class CritfallHorseman {

    public static final String MOD_ID = "critfall_horseman";
    public static final String MOD_NAME = "Critfall: Headless Horseman";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    private static HorsemanConfig config = HorsemanConfig.DEFAULTS;

    private CritfallHorseman() {}

    /** {@code configDir} is the loader's config folder. */
    public static void init(Path configDir) {
        config = HorsemanConfigLoader.load(configDir.resolve(MOD_ID).resolve(MOD_ID + ".json"));
        LOG.info("{} initialized{}", MOD_NAME, config.enabled() ? "" : " (switched off in config)");
    }

    public static HorsemanConfig config() {
        return config;
    }

    /** For tests: replaces the loaded config. */
    public static void useConfig(HorsemanConfig config) {
        CritfallHorseman.config = config;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
