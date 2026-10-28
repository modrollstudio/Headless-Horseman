package studio.modroll.horseman.config;

/**
 * Settings from {@code config/critfall_horseman/critfall_horseman-client.json}: things that only change the player's
 * own screen, so each player keeps their own on a server.
 */
public record HorsemanClientConfig(boolean fog, boolean cameraShake, boolean ambientParticles) {

    public static final HorsemanClientConfig DEFAULTS = new HorsemanClientConfig(true, true, true);
}
