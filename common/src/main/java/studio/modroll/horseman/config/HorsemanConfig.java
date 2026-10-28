package studio.modroll.horseman.config;

import java.time.MonthDay;

/**
 * Settings from {@code config/critfall_horseman/critfall_horseman.json}. On a server the server's file decides.
 * {@code effects} covers the particle bursts the server sends; what only changes one player's screen is in
 * {@link HorsemanClientConfig}.
 *
 * @param enabled master switch
 * @param seasonChance chance per midnight in season, 0 to 1
 */
public record HorsemanConfig(
        boolean enabled,
        boolean naturalSpawning,
        MonthDay halloweenNight,
        MonthDay seasonStart,
        MonthDay seasonEnd,
        boolean allYear,
        double seasonChance,
        boolean sunriseExit,
        boolean fireDamage,
        boolean charge,
        boolean headThrow,
        boolean sweep,
        boolean headPassing,
        boolean bossBar,
        boolean lines,
        boolean hoofbeats,
        boolean drops,
        boolean effects) {

    public static final HorsemanConfig DEFAULTS = new HorsemanConfig(
            true,
            true,
            MonthDay.of(10, 31),
            MonthDay.of(10, 1),
            MonthDay.of(10, 31),
            false,
            0.1,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true);

    public boolean naturalSpawningOn() {
        return enabled && naturalSpawning;
    }
}
