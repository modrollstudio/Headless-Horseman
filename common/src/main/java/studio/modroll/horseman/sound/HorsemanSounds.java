package studio.modroll.horseman.sound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import studio.modroll.horseman.CritfallHorseman;

/**
 * Sound events, matching the {@code sounds.json} that {@code tools/sounds.py} generates. A cue is several events
 * ({@code <cue>_1}, {@code <cue>_2}, ...) played at once as layers.
 */
public final class HorsemanSounds {

    private static final Map<ResourceLocation, SoundEvent> EVENTS = new LinkedHashMap<>();

    public record Cue(List<SoundEvent> layers) {}

    /** LAUGH_1 is the one used on arrival and at sunrise. */
    public static final Cue LAUGH_1 = cue("laugh_1", 4);

    public static final Cue LAUGH_2 = cue("laugh_2", 4);
    public static final Cue LAUGH_3 = cue("laugh_3", 4);
    public static final Cue GROWL = cue("growl", 2);
    public static final Cue ROAR = cue("roar", 3);
    public static final Cue SCREAM = cue("scream", 3);
    public static final Cue HOOFBEATS = cue("hoofbeats", 2);
    public static final Cue STEED_SCREAM = cue("steed_scream", 3);
    public static final Cue STEED_BREATH = cue("steed_breath", 1);
    public static final Cue CACKLE = cue("cackle", 2);
    public static final Cue SWORD_FIRE = cue("sword_fire", 2);
    public static final Cue HEAD_WHOOSH = cue("head_whoosh", 2);
    public static final Cue IMPACT = cue("impact", 2);
    public static final Cue CRUMBLE = cue("crumble", 2);
    public static final Cue FIRE_ROAR = cue("fire_roar", 1);
    public static final Cue HISS = cue("hiss", 2);

    /** Played with his lines, see {@link studio.modroll.horseman.fight.Lines}. */
    public static final Cue STING_ARRIVAL = cue("sting_arrival", 3);

    public static final Cue STING_HEADLESS = cue("sting_headless", 3);
    public static final Cue STING_CHASE = cue("sting_chase", 2);
    public static final Cue STING_RETAKE = cue("sting_retake", 3);
    public static final Cue STING_STEED = cue("sting_steed", 2);
    public static final Cue STING_SUNRISE = cue("sting_sunrise", 3);
    public static final Cue STING_KILL = cue("sting_kill", 2);

    private static final List<Cue> LAUGHS = List.of(LAUGH_1, LAUGH_2, LAUGH_3);

    private HorsemanSounds() {}

    private static Cue cue(String name, int layers) {
        List<SoundEvent> events = new ArrayList<>();
        for (int k = 1; k <= layers; k++) {
            ResourceLocation id = CritfallHorseman.id(name + "_" + k);
            SoundEvent event = SoundEvent.createVariableRangeEvent(id);
            EVENTS.put(id, event);
            events.add(event);
        }
        return new Cue(List.copyOf(events));
    }

    public static void register(BiConsumer<ResourceLocation, SoundEvent> registrar) {
        EVENTS.forEach(registrar);
    }

    /** For tests. */
    public static Map<ResourceLocation, SoundEvent> events() {
        return Collections.unmodifiableMap(EVENTS);
    }

    public static void play(Entity entity, SoundEvent sound, float volume, float pitch) {
        play(entity.level(), entity.position(), sound, volume, pitch);
    }

    public static void play(Level level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        if (!level.isClientSide) {
            level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
        }
    }

    public static void play(Entity entity, Cue cue) {
        play(entity, cue, 3.0F);
    }

    public static void play(Entity entity, Cue cue, float volume) {
        play(entity.level(), entity.position(), cue, volume);
    }

    public static void play(Level level, Vec3 at, Cue cue, float volume) {
        for (SoundEvent layer : cue.layers()) {
            play(level, at, layer, volume, 1.0F);
        }
    }

    /** Plays at the player's own position, so it is heard at full volume wherever the source is. */
    public static void playTo(ServerPlayer player, Cue cue) {
        for (SoundEvent layer : cue.layers()) {
            player.playNotifySound(layer, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    /** The signature laugh, or one of the three at random. */
    public static void laugh(Entity entity, boolean signature) {
        Cue laugh = signature ? LAUGH_1 : LAUGHS.get(entity.level().random.nextInt(LAUGHS.size()));
        play(entity, laugh, 4.0F);
    }
}
