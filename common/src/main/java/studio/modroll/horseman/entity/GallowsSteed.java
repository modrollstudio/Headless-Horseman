package studio.modroll.horseman.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.fight.Effects;
import studio.modroll.horseman.sound.HorsemanSounds;

/**
 * The Horseman's mount, with its own health and Critfall profile. While ridden its movement goals are disabled and
 * the rider steers it. If it dies he fights on foot; if he dies it is killed too.
 */
public class GallowsSteed extends Monster implements GeoEntity {

    /** Passenger attachment height, in blocks: puts the rider's hips on the saddle. */
    public static final float SEAT = 20.0F / 16.0F;

    public static final String ACTIONS = "actions";
    public static final String REAR = "rear";

    public static final int DEATH_TICKS = 42;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation GALLOP = RawAnimation.begin().thenLoop("gallop");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GallowsSteed(EntityType<? extends GallowsSteed> type, Level level) {
        super(type, level);
        this.xpReward = 20;
        // The rider steers through this entity's pathfinding, so this keeps both of them out of water.
        setPathfindingMalus(PathType.WATER, -1.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 16.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // Only used when riderless (spawned from its own egg); a mob rider disables the move goals.
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        HorsemanStats.applyHealth(this, level.getLevel(), "gallows_steed");
        return data;
    }

    public void rear() {
        triggerAnim(ACTIONS, REAR);
        HorsemanSounds.play(this, HorsemanSounds.STEED_SCREAM);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && isAlive() && CritfallHorseman.config().effects()) {
            hoofSparks();
            breathSmoke();
        }
    }

    private void hoofSparks() {
        double speed = getDeltaMovement().horizontalDistanceSqr();
        if (speed < 0.002 || random.nextFloat() > (isGalloping() ? 0.8F : 0.3F)) {
            return;
        }
        double x = getX() + (random.nextDouble() - 0.5) * getBbWidth();
        double z = getZ() + (random.nextDouble() - 0.5) * getBbWidth();
        level().addParticle(ParticleTypes.SMALL_FLAME, x, getY() + 0.05, z, 0.0, 0.03, 0.0);
        if (random.nextInt(6) == 0) {
            level().addParticle(ParticleTypes.LAVA, x, getY() + 0.1, z, 0.0, 0.0, 0.0);
        }
        if (random.nextInt(3) == 0) {
            level().addParticle(
                            ParticleTypes.CRIT,
                            x,
                            getY() + 0.1,
                            z,
                            (random.nextDouble() - 0.5) * 0.3,
                            0.15,
                            (random.nextDouble() - 0.5) * 0.3);
        }
    }

    private void breathSmoke() {
        int every = isGalloping() ? 12 : 30;
        if ((tickCount + getId()) % every > 2) {
            return;
        }
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 nose = position().add(forward.scale(2.1)).add(0, 2.45, 0);
        for (int i = 0; i < 2; i++) {
            level().addParticle(
                            ParticleTypes.SMOKE,
                            nose.x + (random.nextDouble() - 0.5) * 0.25,
                            nose.y,
                            nose.z + (random.nextDouble() - 0.5) * 0.25,
                            forward.x * 0.06,
                            -0.01,
                            forward.z * 0.06);
        }
    }

    /** Gives an idle rider the attacker as his target. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt
                && source.getEntity() instanceof LivingEntity attacker
                && getFirstPassenger() instanceof Horseman rider
                && attacker != rider
                && rider.getTarget() == null) {
            rider.setTarget(attacker);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        boolean wasAlive = !dead;
        super.die(source);
        if (!wasAlive || level().isClientSide) {
            return;
        }
        HorsemanSounds.play(this, HorsemanSounds.STEED_SCREAM);
        if (getFirstPassenger() instanceof Horseman rider && rider.isAlive()) {
            rider.onSteedLost();
        }
    }

    /** Beside the steed on its left, rather than vanilla's default of on top of it. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 left =
                new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw)).scale(getBbWidth() * 0.5 + passenger.getBbWidth() * 0.5 + 0.2);
        Vec3 beside = position().add(left);
        return level().noCollision(passenger, passenger.getBoundingBox().move(beside.subtract(passenger.position())))
                ? beside
                : position();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return HorsemanSounds.STEED_BREATH.layers().get(0);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    public boolean isGalloping() {
        Vec3 motion = getDeltaMovement();
        return motion.horizontalDistanceSqr() > 0.03;
    }

    @Override
    protected void tickDeath() {
        if (++deathTime >= DEATH_TICKS && !level().isClientSide && !isRemoved()) {
            Effects.steedCrumbles(this);
            HorsemanSounds.play(this, HorsemanSounds.CRUMBLE, 2.0F);
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 4, state -> {
            if (isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (!state.isMoving()) {
                return state.setAndContinue(IDLE);
            }
            return state.setAndContinue(isGalloping() ? GALLOP : WALK);
        }));
        controllers.add(new AnimationController<>(this, ACTIONS, 2, state -> PlayState.STOP)
                .triggerableAnim(REAR, RawAnimation.begin().thenPlay(REAR)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
