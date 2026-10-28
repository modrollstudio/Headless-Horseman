package studio.modroll.horseman.fight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import studio.modroll.horseman.CritfallHorseman;

/**
 * One-off particle bursts sent from the server. Particles only, never block changes. All of them do nothing while the
 * {@code effects} config switch is off. Continuous effects (smoke, embers, sparks) are spawned client-side by the
 * entities themselves.
 */
public final class Effects {

    private Effects() {}

    private static boolean on(Entity entity) {
        return CritfallHorseman.config().effects() && entity.level() instanceof ServerLevel;
    }

    private static void burst(Entity at, ParticleOptions particle, Vec3 pos, int count, double spread, double speed) {
        ((ServerLevel) at.level()).sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    public static void fireArc(Entity horseman) {
        if (!on(horseman)) {
            return;
        }
        float yaw = horseman.getYRot() * Mth.DEG_TO_RAD;
        Vec3 centre = horseman.position().add(0, horseman.getBbHeight() * 0.6, 0);
        for (int i = 0; i <= 12; i++) {
            float a = yaw + (i / 12.0F - 0.5F) * Mth.PI * 0.9F;
            Vec3 point = centre.add(-Mth.sin(a) * 2.2, (i / 12.0 - 0.5) * -0.8, Mth.cos(a) * 2.2);
            burst(horseman, ParticleTypes.FLAME, point, 2, 0.05, 0.01);
        }
    }

    /** A ring of fire {@code radius} blocks out, for the sweep. */
    public static void fireRing(Entity horseman, double radius) {
        if (!on(horseman)) {
            return;
        }
        Vec3 centre = horseman.position().add(0, horseman.getBbHeight() * 0.6, 0);
        for (int i = 0; i < 36; i++) {
            float a = i / 36.0F * Mth.TWO_PI;
            for (double r = 1.5; r <= radius; r += 1.25) {
                Vec3 point = centre.add(Mth.cos(a) * r, 0, Mth.sin(a) * r);
                burst(horseman, ParticleTypes.FLAME, point, 1, 0.08, 0.01);
            }
        }
        burst(horseman, ParticleTypes.SWEEP_ATTACK, centre, 6, radius * 0.4, 0.0);
    }

    public static void impact(Entity victim) {
        if (!on(victim)) {
            return;
        }
        Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
        burst(victim, ParticleTypes.CRIT, at, 20, 0.4, 0.4);
        burst(victim, ParticleTypes.LAVA, at, 4, 0.3, 0.1);
        BlockState ground = victim.level()
                .getBlockState(BlockPos.containing(victim.position()).below());
        if (!ground.isAir()) {
            burst(victim, new BlockParticleOption(ParticleTypes.BLOCK, ground), victim.position(), 30, 0.6, 0.15);
        }
    }

    public static void neckFlare(Entity horseman) {
        if (!on(horseman)) {
            return;
        }
        Vec3 neck = horseman.position().add(0, horseman.getBbHeight() * 0.95, 0);
        burst(horseman, ParticleTypes.FLAME, neck, 30, 0.2, 0.08);
        burst(horseman, ParticleTypes.LAVA, neck, 6, 0.2, 0.1);
        burst(horseman, ParticleTypes.LARGE_SMOKE, neck, 12, 0.2, 0.05);
    }

    public static void retake(Entity horseman) {
        if (!on(horseman)) {
            return;
        }
        Vec3 at = horseman.position().add(0, horseman.getBbHeight() * 0.7, 0);
        burst(horseman, ParticleTypes.FLAME, at, 60, 0.6, 0.15);
        burst(horseman, ParticleTypes.SOUL_FIRE_FLAME, at, 10, 0.4, 0.05);
        burst(horseman, ParticleTypes.LAVA, at, 8, 0.4, 0.1);
    }

    public static void burningHead(Entity at, Vec3 pos) {
        if (!on(at)) {
            return;
        }
        burst(at, ParticleTypes.FLAME, pos.add(0, 0.4, 0), 2, 0.12, 0.02);
        burst(at, ParticleTypes.SMOKE, pos.add(0, 0.6, 0), 1, 0.1, 0.02);
    }

    /** One point of the line of fire; called repeatedly while the line burns. */
    public static void groundFire(Entity at, Vec3 ground) {
        if (!on(at)) {
            return;
        }
        burst(at, ParticleTypes.FLAME, ground.add(0, 0.15, 0), 4, 0.22, 0.012);
        burst(at, ParticleTypes.SMALL_FLAME, ground.add(0, 0.05, 0), 2, 0.25, 0.005);
        if (at.level().random.nextInt(3) == 0) {
            burst(at, ParticleTypes.SMOKE, ground.add(0, 0.5, 0), 1, 0.2, 0.02);
        }
        if (at.level().random.nextInt(12) == 0) {
            burst(at, ParticleTypes.LAVA, ground.add(0, 0.1, 0), 1, 0.2, 0.0);
        }
    }

    public static void headLands(Entity at, Vec3 pos) {
        if (!on(at)) {
            return;
        }
        burst(at, ParticleTypes.FLAME, pos.add(0, 0.4, 0), 70, 0.5, 0.14);
        burst(at, ParticleTypes.LAVA, pos.add(0, 0.3, 0), 18, 0.4, 0.1);
        burst(at, ParticleTypes.LARGE_SMOKE, pos.add(0, 0.8, 0), 30, 0.6, 0.06);
        burst(at, ParticleTypes.SMALL_FLAME, pos.add(0, 0.2, 0), 40, 0.9, 0.04);
    }

    public static void steam(Entity at, Vec3 pos) {
        if (!on(at)) {
            return;
        }
        burst(at, ParticleTypes.CLOUD, pos.add(0, 0.6, 0), 40, 0.5, 0.06);
        burst(at, ParticleTypes.WHITE_SMOKE, pos.add(0, 0.8, 0), 30, 0.5, 0.05);
        burst(at, ParticleTypes.SMOKE, pos.add(0, 0.5, 0), 20, 0.4, 0.04);
    }

    public static void smokeOut(Entity at, Vec3 pos) {
        if (!on(at)) {
            return;
        }
        burst(at, ParticleTypes.LARGE_SMOKE, pos.add(0, 0.6, 0), 30, 0.4, 0.05);
        burst(at, ParticleTypes.FLAME, pos.add(0, 0.4, 0), 15, 0.3, 0.05);
    }

    public static void steedCrumbles(Entity steed) {
        if (!on(steed)) {
            return;
        }
        Vec3 at = steed.position().add(0, 0.6, 0);
        burst(steed, ParticleTypes.LAVA, at, 20, 0.8, 0.1);
        burst(steed, ParticleTypes.LARGE_SMOKE, at, 30, 0.9, 0.03);
        burst(steed, ParticleTypes.WHITE_ASH, at, 80, 1.0, 0.05);
        burst(steed, ParticleTypes.FLAME, at, 20, 0.8, 0.02);
    }

    public static void horsemanCrumbles(Entity horseman) {
        if (!on(horseman)) {
            return;
        }
        Vec3 at = horseman.position().add(0, 0.5, 0);
        burst(horseman, ParticleTypes.LARGE_SMOKE, at, 40, 0.6, 0.04);
        burst(horseman, ParticleTypes.SMOKE, at, 40, 0.6, 0.03);
        burst(horseman, ParticleTypes.LAVA, at, 16, 0.5, 0.1);
        burst(horseman, ParticleTypes.WHITE_ASH, at, 60, 0.8, 0.05);
    }

    public static void vanish(Entity horseman) {
        if (!on(horseman)) {
            return;
        }
        Vec3 at = horseman.position().add(0, 1.2, 0);
        burst(horseman, ParticleTypes.LARGE_SMOKE, at, 80, 1.0, 0.05);
        burst(horseman, ParticleTypes.FLAME, at, 20, 0.8, 0.03);
    }

    /** A ring of mist {@code radius} blocks around {@code centre}. */
    public static void mistRing(ServerLevel level, Vec3 centre, double radius) {
        if (!CritfallHorseman.config().effects()) {
            return;
        }
        for (int i = 0; i < 24; i++) {
            double a = level.random.nextDouble() * Mth.TWO_PI;
            double r = radius + (level.random.nextDouble() - 0.5) * 2.0;
            double x = centre.x + Math.cos(a) * r;
            double z = centre.z + Math.sin(a) * r;
            level.sendParticles(
                    ParticleTypes.CLOUD,
                    x,
                    centre.y + 0.3 + level.random.nextDouble() * 1.5,
                    z,
                    1,
                    0.3,
                    0.2,
                    0.3,
                    0.005);
        }
    }
}
