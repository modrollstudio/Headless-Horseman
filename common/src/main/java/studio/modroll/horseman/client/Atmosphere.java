package studio.modroll.horseman.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.entity.Horseman;

/**
 * Client-side fog and camera shake near the Horseman, computed from the entities the client already has, so no
 * packets are needed. NeoForge applies them through its viewport events, Fabric through two mixins.
 */
public final class Atmosphere {

    /** Fog starts at this distance and is at full strength within {@link #FOG_FULL}. */
    public static final double FOG_RANGE = 48.0;

    public static final double FOG_FULL = 16.0;
    /** Fog end distance at full strength. */
    public static final float FOG_END = 22.0F;

    public static final double SHAKE_RANGE = 20.0;

    private static long lastTick = Long.MIN_VALUE;
    private static float fog;
    private static float fogO;
    private static float shake;

    private Atmosphere() {}

    /** Recomputes at most once per game tick, however often it's called from the render hooks. */
    public static void update() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            fog = fogO = shake = 0.0F;
            return;
        }
        long now = minecraft.level.getGameTime();
        if (now == lastTick) {
            return;
        }
        lastTick = now;
        double nearest = Double.MAX_VALUE;
        double charging = Double.MAX_VALUE;
        for (Horseman horseman : minecraft.level.getEntitiesOfClass(
                Horseman.class, minecraft.player.getBoundingBox().inflate(FOG_RANGE), Horseman::isAlive)) {
            double distance = horseman.distanceTo(minecraft.player);
            nearest = Math.min(nearest, distance);
            if (horseman.isCharging()) {
                charging = Math.min(charging, distance);
            }
        }
        float wanted = CritfallHorseman.clientConfig().fog() && nearest < FOG_RANGE
                ? (float) Mth.clamp(1.0 - (nearest - FOG_FULL) / (FOG_RANGE - FOG_FULL), 0.0, 1.0)
                : 0.0F;
        fogO = fog;
        fog += (wanted - fog) * 0.03F;
        shake = CritfallHorseman.clientConfig().cameraShake() && charging < SHAKE_RANGE
                ? (float) (1.0 - charging / SHAKE_RANGE)
                : 0.0F;
    }

    /** 0 to 1. */
    public static float fog(float partialTick) {
        update();
        return Mth.lerp(partialTick, fogO, fog);
    }

    public static float fogStart(float start, float partialTick) {
        return Mth.lerp(fog(partialTick), start, 0.0F);
    }

    public static float fogEnd(float end, float partialTick) {
        return Mth.lerp(fog(partialTick), end, Math.min(end, FOG_END));
    }

    public static boolean foggy(float partialTick) {
        return fog(partialTick) > 0.001F;
    }

    /** 0 to 1. */
    public static float shake() {
        update();
        return shake;
    }

    /** Degrees. */
    public static float shakeRoll(float partialTick) {
        float time = lastTick + partialTick;
        return shake() * 1.4F * (Mth.sin(time * 2.1F) + 0.5F * Mth.sin(time * 5.3F));
    }

    /** Degrees. */
    public static float shakePitch(float partialTick) {
        float time = lastTick + partialTick;
        return shake() * 0.9F * Mth.sin(time * 3.7F + 1.0F);
    }
}
