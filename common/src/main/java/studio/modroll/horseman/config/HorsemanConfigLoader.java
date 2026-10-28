package studio.modroll.horseman.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.MonthDay;
import java.util.Set;
import java.util.function.Consumer;
import studio.modroll.horseman.CritfallHorseman;

/**
 * Reads the config files, writing the defaults if one is missing. Invalid values fall back to their default with a
 * warning; an unreadable file means all defaults. Dates are {@code MM-DD}.
 */
public final class HorsemanConfigLoader {

    public static final String DEFAULT_FILE = """
            {
              "format_version": 1,
              "enabled": true,
              "natural_spawning": true,
              "halloween_night": "10-31",
              "season_start": "10-01",
              "season_end": "10-31",
              "all_year": false,
              "season_chance": 0.1,
              "sunrise_exit": true,
              "fire_damage": true,
              "charge": true,
              "head_throw": true,
              "sweep": true,
              "head_passing": true,
              "boss_bar": true,
              "lines": true,
              "hoofbeats": true,
              "drops": true,
              "effects": true
            }
            """;

    public static final String DEFAULT_CLIENT_FILE = """
            {
              "format_version": 1,
              "fog": true,
              "camera_shake": true,
              "ambient_particles": true
            }
            """;

    private static final Set<String> KNOWN_KEYS = Set.of(
            "format_version",
            "enabled",
            "natural_spawning",
            "halloween_night",
            "season_start",
            "season_end",
            "all_year",
            "season_chance",
            "sunrise_exit",
            "fire_damage",
            "charge",
            "head_throw",
            "sweep",
            "head_passing",
            "boss_bar",
            "lines",
            "hoofbeats",
            "drops",
            "effects");

    private static final Set<String> KNOWN_CLIENT_KEYS =
            Set.of("format_version", "fog", "camera_shake", "ambient_particles");

    private HorsemanConfigLoader() {}

    public static HorsemanConfig load(Path file) {
        JsonObject json = read(file, DEFAULT_FILE);
        return json == null
                ? HorsemanConfig.DEFAULTS
                : parse(json, warning -> CritfallHorseman.LOG.warn("{}: {}", file, warning));
    }

    public static HorsemanClientConfig loadClient(Path file) {
        JsonObject json = read(file, DEFAULT_CLIENT_FILE);
        return json == null
                ? HorsemanClientConfig.DEFAULTS
                : parseClient(json, warning -> CritfallHorseman.LOG.warn("{}: {}", file, warning));
    }

    /** The file as JSON, written with {@code defaultFile} first if missing; null if it can't be read. */
    private static JsonObject read(Path file, String defaultFile) {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, defaultFile);
            }
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException | JsonParseException | IllegalStateException e) {
            CritfallHorseman.LOG.warn("Could not read {}, using the defaults: {}", file, e.getMessage());
            return null;
        }
    }

    public static HorsemanConfig parse(JsonObject json, Consumer<String> warn) {
        for (String key : json.keySet()) {
            if (KNOWN_CLIENT_KEYS.contains(key) && !KNOWN_KEYS.contains(key)) {
                // Before 0.1.1 these were in this file.
                warn.accept("\"" + key + "\" is now set in critfall_horseman-client.json, ignored here");
            } else if (!KNOWN_KEYS.contains(key)) {
                warn.accept("unknown key \"" + key + "\" ignored");
            }
        }
        HorsemanConfig d = HorsemanConfig.DEFAULTS;
        return new HorsemanConfig(
                bool(json, "enabled", d.enabled(), warn),
                bool(json, "natural_spawning", d.naturalSpawning(), warn),
                date(json, "halloween_night", d.halloweenNight(), warn),
                date(json, "season_start", d.seasonStart(), warn),
                date(json, "season_end", d.seasonEnd(), warn),
                bool(json, "all_year", d.allYear(), warn),
                chance(json, "season_chance", d.seasonChance(), warn),
                bool(json, "sunrise_exit", d.sunriseExit(), warn),
                bool(json, "fire_damage", d.fireDamage(), warn),
                bool(json, "charge", d.charge(), warn),
                bool(json, "head_throw", d.headThrow(), warn),
                bool(json, "sweep", d.sweep(), warn),
                bool(json, "head_passing", d.headPassing(), warn),
                bool(json, "boss_bar", d.bossBar(), warn),
                bool(json, "lines", d.lines(), warn),
                bool(json, "hoofbeats", d.hoofbeats(), warn),
                bool(json, "drops", d.drops(), warn),
                bool(json, "effects", d.effects(), warn));
    }

    public static HorsemanClientConfig parseClient(JsonObject json, Consumer<String> warn) {
        for (String key : json.keySet()) {
            if (!KNOWN_CLIENT_KEYS.contains(key)) {
                warn.accept("unknown key \"" + key + "\" ignored");
            }
        }
        HorsemanClientConfig d = HorsemanClientConfig.DEFAULTS;
        return new HorsemanClientConfig(
                bool(json, "fog", d.fog(), warn),
                bool(json, "camera_shake", d.cameraShake(), warn),
                bool(json, "ambient_particles", d.ambientParticles(), warn));
    }

    private static boolean bool(JsonObject json, String key, boolean fallback, Consumer<String> warn) {
        JsonElement value = json.get(key);
        if (value == null) {
            return fallback;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        warn.accept("\"" + key + "\" must be true or false, using " + fallback);
        return fallback;
    }

    private static double chance(JsonObject json, String key, double fallback, Consumer<String> warn) {
        JsonElement value = json.get(key);
        if (value == null) {
            return fallback;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            double chance = value.getAsDouble();
            if (chance >= 0 && chance <= 1) {
                return chance;
            }
        }
        warn.accept("\"" + key + "\" must be a number from 0 to 1, using " + fallback);
        return fallback;
    }

    private static MonthDay date(JsonObject json, String key, MonthDay fallback, Consumer<String> warn) {
        JsonElement value = json.get(key);
        if (value == null) {
            return fallback;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            try {
                return MonthDay.parse("--" + value.getAsString());
            } catch (DateTimeException e) {
                // Reported below.
            }
        }
        warn.accept("\"" + key + "\" must be a date as MM-DD, using "
                + String.format("%02d-%02d", fallback.getMonthValue(), fallback.getDayOfMonth()));
        return fallback;
    }
}
