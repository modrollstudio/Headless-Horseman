package studio.modroll.horseman.config;

import java.util.List;

/** Every key the config screen edits, in the order it shows them. Labels are {@code critfall_horseman.config.key.*}. */
public final class ConfigSettings {

    public enum Kind {
        TOGGLE,
        /** {@code MM-DD}. */
        DATE,
        /** A number from 0 to 1. */
        CHANCE
    }

    public record Setting(String key, Kind kind, String section) {}

    /** The keys of {@code critfall_horseman.json}. */
    public static final List<Setting> SERVER = List.of(
            new Setting("enabled", Kind.TOGGLE, "general"),
            new Setting("natural_spawning", Kind.TOGGLE, "spawning"),
            new Setting("halloween_night", Kind.DATE, "spawning"),
            new Setting("season_start", Kind.DATE, "spawning"),
            new Setting("season_end", Kind.DATE, "spawning"),
            new Setting("all_year", Kind.TOGGLE, "spawning"),
            new Setting("season_chance", Kind.CHANCE, "spawning"),
            new Setting("hoofbeats", Kind.TOGGLE, "spawning"),
            new Setting("sunrise_exit", Kind.TOGGLE, "spawning"),
            new Setting("fire_damage", Kind.TOGGLE, "fight"),
            new Setting("charge", Kind.TOGGLE, "fight"),
            new Setting("head_throw", Kind.TOGGLE, "fight"),
            new Setting("sweep", Kind.TOGGLE, "fight"),
            new Setting("head_passing", Kind.TOGGLE, "fight"),
            new Setting("drops", Kind.TOGGLE, "fight"),
            new Setting("boss_bar", Kind.TOGGLE, "display"),
            new Setting("lines", Kind.TOGGLE, "display"),
            new Setting("effects", Kind.TOGGLE, "display"));

    /** The keys of {@code critfall_horseman-client.json}. */
    public static final List<Setting> CLIENT = List.of(
            new Setting("fog", Kind.TOGGLE, "screen"),
            new Setting("camera_shake", Kind.TOGGLE, "screen"),
            new Setting("ambient_particles", Kind.TOGGLE, "screen"));

    private ConfigSettings() {}
}
