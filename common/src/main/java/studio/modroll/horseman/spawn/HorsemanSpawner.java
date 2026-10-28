package studio.modroll.horseman.spawn;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.config.HorsemanConfig;
import studio.modroll.horseman.entity.GallowsSteed;
import studio.modroll.horseman.entity.Horseman;
import studio.modroll.horseman.entity.HorsemanEntities;
import studio.modroll.horseman.fight.Effects;
import studio.modroll.horseman.fight.Lines;
import studio.modroll.horseman.sound.HorsemanSounds;

/**
 * Natural spawning. At midnight in the Overworld, if {@link HorsemanCalendar} allows it, he comes for a random survival
 * player under the open sky, after a few seconds of approaching hoofbeats and mist.
 *
 * <p>{@link #spawn} is the single entry point for putting him in a world, for natural arrivals and for other mods.
 */
public final class HorsemanSpawner {

    /** Time of day in ticks. */
    public static final long MIDNIGHT = 18000L;

    public static final int ARRIVAL_TICKS = 140;
    public static final double ARRIVAL_DISTANCE = 14.0;

    /** Real-world date source; replaced in tests. */
    private static Clock clock = Clock.systemDefaultZone();

    private static final List<Arrival> ARRIVALS = new ArrayList<>();

    private HorsemanSpawner() {}

    private static final class Arrival {
        final UUID player;
        final Vec3 from;
        int ticks;

        Arrival(UUID player, Vec3 from) {
            this.player = player;
            this.from = from;
        }
    }

    public static void useClock(Clock clock) {
        HorsemanSpawner.clock = clock;
    }

    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Spawns him mounted at {@code at}, facing and targeting {@code target} if given, with his arrival effects. */
    public static Horseman spawn(ServerLevel level, Vec3 at, @Nullable LivingEntity target) {
        Horseman horseman = HorsemanEntities.HORSEMAN.create(level);
        if (horseman == null) {
            throw new IllegalStateException("the Horseman's entity type could not create him");
        }
        float yaw = target == null
                ? level.random.nextFloat() * 360.0F
                : (float) (Mth.atan2(target.getZ() - at.z, target.getX() - at.x) * Mth.RAD_TO_DEG) - 90.0F;
        horseman.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        horseman.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
        GallowsSteed steed = horseman.steed();
        if (steed != null) {
            steed.moveTo(at.x, at.y, at.z, yaw, 0.0F);
            steed.setYBodyRot(yaw);
            steed.setYHeadRot(yaw);
        }
        level.addFreshEntity(horseman);
        if (target != null) {
            horseman.setTarget(target);
        }
        if (steed != null) {
            steed.rear();
        }
        HorsemanSounds.laugh(horseman, true);
        horseman.say(Lines.Line.ARRIVAL);
        return horseman;
    }

    /** Called every level tick; only acts in the Overworld. */
    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        tickArrivals(level);
        HorsemanConfig config = CritfallHorseman.config();
        if (!config.naturalSpawningOn() || Math.floorMod(level.getDayTime(), 24000L) != MIDNIGHT) {
            return;
        }
        HorsemanSavedData data = HorsemanSavedData.get(level);
        long day = Math.floorDiv(level.getDayTime(), 24000L);
        if (data.lastMidnight() == day) {
            return;
        }
        data.setLastMidnight(day);
        midnight(level, level.random.nextDouble());
    }

    /**
     * Decides whether he comes tonight, using {@code roll} (0 to 1) for the chance, and starts his arrival. Returns the
     * player he comes for, or null.
     */
    @Nullable
    public static ServerPlayer midnight(ServerLevel level, double roll) {
        HorsemanConfig config = CritfallHorseman.config();
        if (!config.naturalSpawningOn()
                || !ARRIVALS.isEmpty()
                || !level.getEntities(HorsemanEntities.HORSEMAN, Horseman::isAlive)
                        .isEmpty()) {
            return null;
        }
        LocalDate today = today();
        HorsemanSavedData data = HorsemanSavedData.get(level);
        boolean halloween = HorsemanCalendar.night(today, config) == HorsemanCalendar.Night.HALLOWEEN;
        if (!HorsemanCalendar.comes(today, config, data.lastHalloweenYear() == today.getYear(), roll)) {
            return null;
        }
        List<ServerPlayer> underSky = level.players().stream()
                .filter(player -> player.isAlive()
                        && !player.isSpectator()
                        && !player.isCreative()
                        && level.canSeeSky(player.blockPosition().above()))
                .toList();
        if (underSky.isEmpty()) {
            return null;
        }
        if (halloween) {
            data.setLastHalloweenYear(today.getYear());
        }
        ServerPlayer player = underSky.get(level.random.nextInt(underSky.size()));
        arrive(level, player);
        return player;
    }

    /** Starts the hoofbeats, or spawns him immediately if hoofbeats are switched off. */
    public static void arrive(ServerLevel level, ServerPlayer player) {
        float a = level.random.nextFloat() * Mth.TWO_PI;
        Vec3 from = new Vec3(Mth.cos(a), 0, Mth.sin(a));
        if (!CritfallHorseman.config().hoofbeats()) {
            spawn(level, arrivalPoint(level, player, from), player);
            return;
        }
        ARRIVALS.add(new Arrival(player.getUUID(), from));
    }

    private static void tickArrivals(ServerLevel level) {
        Iterator<Arrival> it = ARRIVALS.iterator();
        while (it.hasNext()) {
            Arrival arrival = it.next();
            if (!(level.getPlayerByUUID(arrival.player) instanceof ServerPlayer player) || !player.isAlive()) {
                it.remove();
                continue;
            }
            arrival.ticks++;
            double near = (double) arrival.ticks / ARRIVAL_TICKS;
            if (arrival.ticks % 10 == 0) {
                Vec3 hooves = player.position().add(arrival.from.scale(Mth.lerp(near, 40.0, ARRIVAL_DISTANCE)));
                float volume = (float) Mth.lerp(near, 0.6, 3.0);
                HorsemanSounds.play(level, hooves, HorsemanSounds.HOOFBEATS, volume);
            }
            if (arrival.ticks % 4 == 0) {
                Effects.mistRing(level, player.position(), Mth.lerp(near, 16.0, 6.0));
            }
            if (arrival.ticks >= ARRIVAL_TICKS) {
                it.remove();
                spawn(level, arrivalPoint(level, player, arrival.from), player);
            }
        }
    }

    /** Ground position {@link #ARRIVAL_DISTANCE} from {@code player} in direction {@code from}. */
    private static Vec3 arrivalPoint(ServerLevel level, ServerPlayer player, Vec3 from) {
        Vec3 out = player.position().add(from.scale(ARRIVAL_DISTANCE));
        int x = Mth.floor(out.x);
        int z = Mth.floor(out.z);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - player.getY()) > 8) {
            return player.position().add(from.scale(4.0));
        }
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    /** For tests. */
    public static void clearArrivals() {
        ARRIVALS.clear();
    }
}
