package studio.modroll.horseman.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
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
import studio.modroll.horseman.fight.Head;
import studio.modroll.horseman.fight.HorsemanCombat;
import studio.modroll.horseman.fight.Lines;
import studio.modroll.horseman.item.HorsemanItems;
import studio.modroll.horseman.sound.HorsemanSounds;

/**
 * The boss. Rides a {@link GallowsSteed} and fights on foot once it dies.
 *
 * <p>With his head he has burning sword hits, a charge and a head throw. Without it he chases whoever holds the head
 * (or the spot it lies on) until he takes it back. All dice go through Critfall, see {@link HorsemanCombat}. If still
 * alive at sunrise he rides off.
 *
 * <p>Animation controllers: body (idle, walk, ride, charge, death), reach (left arm while headless), actions
 * (triggered swings, sweeps and the throw) and fire (the head's flame, always running).
 */
public class Horseman extends Monster implements GeoEntity {

    public static final String ACTIONS = "actions";
    public static final String SWING = "swing";
    public static final String SWING_FOOT = "swing_foot";
    public static final String THROW_HEAD = "throw_head";
    public static final String SWEEP = "sweep";
    public static final String SWEEP_MOUNTED = "sweep_mounted";

    public static final int DEATH_TICKS = 50;

    /** Navigation speed modifiers; mounted they apply to the steed's speed. */
    public static final double RIDE_SPEED = 1.3;

    public static final double CHARGE_SPEED = 2.0;
    public static final double WALK_SPEED = 1.1;
    public static final double RUN_SPEED = 1.5;

    /** Delays from triggering an animation to the moment it should take effect, matched to the animation files. */
    public static final int SWING_IMPACT_MOUNTED = 8;

    public static final int SWING_IMPACT_FOOT = 11;
    public static final int THROW_RELEASE = 11;

    public static final int SWING_COOLDOWN = 40;
    public static final double SWORD_REACH = 4.0;
    /** Degrees per tick when turning to face a target on foot. */
    public static final float TURN_SPEED = 40.0F;
    /** A target this close, or behind him, counts towards a sweep. */
    public static final double CROWD_DISTANCE = 2.5;
    /** Dot product with his facing below which a target counts as behind him (about 100 degrees). */
    public static final double BEHIND = -0.17;

    public static final int SWEEP_AFTER = 60;
    public static final int SWEEP_COOLDOWN = 120;
    public static final int SWEEP_IMPACT_FOOT = 10;
    public static final int SWEEP_IMPACT_MOUNTED = 9;
    public static final int CHARGE_COOLDOWN = 160;
    public static final int CHARGE_MAX_TICKS = 70;
    /** Counted from when he gets his head back. */
    public static final int THROW_COOLDOWN = 240;
    /** If the head can't be found for this long (in a chest, say), it returns to him on its own. */
    public static final int HEAD_LOST_TICKS = 200;

    public static final int FIRE_LINE_TICKS = 80;
    public static final double FIRE_LINE_SPACING = 0.4;
    /** Delay before a doused or unreachable head flies back. */
    public static final int HEAD_RETURN_TICKS = 60;

    public static final int HEAD_UNREACHABLE_TICKS = 60;
    public static final int EXIT_TICKS = 60;
    public static final int TRAMPLE_AGAIN_TICKS = 30;
    /** Per line, for the lines that can repeat in a fight. */
    public static final int LINE_COOLDOWN = 100;

    public static final double BOSS_BAR_RANGE = 64.0;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RIDE = RawAnimation.begin().thenLoop("ride");
    private static final RawAnimation CHARGE = RawAnimation.begin().thenLoop("charge");
    private static final RawAnimation REACH = RawAnimation.begin().thenLoop("headless_reach");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation HEAD_FIRE = RawAnimation.begin().thenLoop("head_fire");

    private static final EntityDataAccessor<Boolean> HEADLESS =
            SynchedEntityData.defineId(Horseman.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CHARGING =
            SynchedEntityData.defineId(Horseman.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable("entity.critfall_horseman.horseman"),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private final ServerBossEvent steedBar = new ServerBossEvent(
            Component.translatable("entity.critfall_horseman.gallows_steed"),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);

    private int headGeneration;
    private int headMissingTicks;

    @Nullable
    private Head.Whereabouts whereabouts;

    @Nullable
    private UUID lastCarrier;

    private int swingCooldown;
    private int sweepCooldown;
    private int pendingSweep;
    private final Map<UUID, Integer> crowding = new HashMap<>();
    private int chargeCooldown = 20;
    private int throwCooldown = 60;
    private int pendingSwing;
    private int pendingThrow;
    private Vec3 throwAt = Vec3.ZERO;

    @Nullable
    private Vec3 chargeTo;

    private int chargeTicks;
    private final Map<UUID, Integer> trampled = new HashMap<>();
    /** Players seen dead nearby, so each death is reacted to once. */
    private final Set<UUID> mourned = new HashSet<>();

    private final List<Vec3> fireLine = new ArrayList<>();

    @Nullable
    private Vec3 lastFireAt;

    @Nullable
    private Vec3 recallFrom;

    private int recallTicks = -1;
    private int headUnreachableTicks;
    private int fireLineTicks;
    private final Map<Lines.Line, Integer> lastSaid = new EnumMap<>(Lines.Line.class);
    private boolean arrivedAtNight;
    private int exitTicks = -1;

    public Horseman(EntityType<? extends Horseman> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        setPathfindingMalus(PathType.WATER, -1.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 16.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HEADLESS, false);
        builder.define(CHARGING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FightGoal());
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    /** Spawns his steed and mounts it, whatever spawned him (egg, command or {@code HorsemanSpawner}). */
    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        HorsemanStats.applyHealth(this, level.getLevel(), "horseman");
        arrivedAtNight = !isDawn(level.getLevel().getDayTime());
        if (!isPassenger()) {
            GallowsSteed steed = HorsemanEntities.STEED.create(level.getLevel());
            if (steed != null) {
                steed.moveTo(getX(), getY(), getZ(), getYRot(), 0.0F);
                steed.finalizeSpawn(level, difficulty, spawnType, null);
                level.addFreshEntityWithPassengers(steed);
                startRiding(steed, true);
            }
        }
        return data;
    }

    public boolean isHeadless() {
        return entityData.get(HEADLESS);
    }

    public void setHeadless(boolean headless) {
        entityData.set(HEADLESS, headless);
    }

    public boolean isCharging() {
        return entityData.get(CHARGING);
    }

    public void setCharging(boolean charging) {
        entityData.set(CHARGING, charging);
    }

    /** Bumped each time he gets his head back, so stale copies of the head item can be detected. */
    public int headGeneration() {
        return headGeneration;
    }

    @Nullable
    public Head.Whereabouts whereabouts() {
        return whereabouts;
    }

    public boolean isLeaving() {
        return exitTicks >= 0;
    }

    public void setArrivedAtNight(boolean arrivedAtNight) {
        this.arrivedAtNight = arrivedAtNight;
    }

    @Nullable
    public GallowsSteed steed() {
        return getVehicle() instanceof GallowsSteed steed ? steed : null;
    }

    /** True from just before sunrise until sunset. */
    public static boolean isDawn(long dayTime) {
        long time = Math.floorMod(dayTime, 24000L);
        return time >= 23000L || time < 12000L;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            if (isAlive()) {
                serverTick(server);
            }
        } else if (isAlive() && CritfallHorseman.config().effects()) {
            clientEffects();
        }
    }

    private void serverTick(ServerLevel level) {
        swingCooldown = Math.max(0, swingCooldown - 1);
        chargeCooldown = Math.max(0, chargeCooldown - 1);
        if (!isHeadless()) {
            throwCooldown = Math.max(0, throwCooldown - 1);
        }
        sweepCooldown = Math.max(0, sweepCooldown - 1);
        if (pendingSwing > 0 && --pendingSwing == 0) {
            landSwing();
        }
        if (pendingSweep > 0 && --pendingSweep == 0) {
            landSweep();
        }
        watchCrowding(level);
        if (pendingThrow > 0 && --pendingThrow == 0) {
            throwHead(throwAt);
        }
        if (isCharging() && !isLeaving()) {
            trampleTick(level);
        }
        trackHead(level);
        if (recallTicks > 0 && --recallTicks == 0) {
            if (isHeadless() && recallFrom != null) {
                level.addFreshEntity(ThrownHead.flyingBack(this, recallFrom));
                HorsemanSounds.play(this, HorsemanSounds.HEAD_WHOOSH, 2.0F);
            }
            recallFrom = null;
        }
        watchForDeaths(level);
        burnFireLine();
        updateBossBars(level);
        if (exitTicks >= 0) {
            if (--exitTicks <= 0) {
                vanish();
            }
        } else if (tickCount % 20 == 0
                && arrivedAtNight
                && CritfallHorseman.config().sunriseExit()
                && isDawn(level.getDayTime())) {
            rideOff();
        }
    }

    /** Keeps him facing the way the steed faces; otherwise his look control would turn him in the saddle. */
    @Override
    public void rideTick() {
        super.rideTick();
        if (getVehicle() instanceof GallowsSteed steed) {
            setYRot(steed.getYRot());
            yRotO = steed.yRotO;
            yBodyRot = steed.yBodyRot;
            yBodyRotO = steed.yBodyRotO;
            yHeadRot = steed.yBodyRot;
            yHeadRotO = steed.yBodyRotO;
        }
    }

    /** Close enough to pick up the head from or take it off a player. */
    public boolean reaches(Entity target) {
        return getAttackBoundingBox().inflate(0.3).intersects(target.getBoundingBox());
    }

    /**
     * Sword reach: {@link #SWORD_REACH} blocks horizontally to the nearest edge of the target's box, in any direction,
     * so circling him or his steed doesn't avoid it.
     */
    public boolean swordReaches(Entity target) {
        AABB box = target.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - getX(), getX() - box.maxX), 0.0);
        double dz = Math.max(Math.max(box.minZ - getZ(), getZ() - box.maxZ), 0.0);
        double bottom = (getVehicle() != null ? getVehicle().getY() : getY()) - 1.0;
        return dx * dx + dz * dz <= SWORD_REACH * SWORD_REACH
                && box.maxY >= bottom
                && box.minY <= getY() + getBbHeight() + 1.0;
    }

    public void startSwing() {
        swingCooldown = SWING_COOLDOWN;
        pendingSwing = isPassenger() ? SWING_IMPACT_MOUNTED : SWING_IMPACT_FOOT;
        triggerAnim(ACTIONS, isPassenger() ? SWING : SWING_FOOT);
    }

    private void landSwing() {
        LivingEntity target = getTarget();
        boolean burning = !isHeadless() && CritfallHorseman.config().fireDamage();
        if (burning) {
            HorsemanSounds.play(this, HorsemanSounds.SWORD_FIRE, 1.5F);
        } else {
            HorsemanSounds.play(this, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.7F);
        }
        if (burning) {
            Effects.fireArc(this);
        }
        if (target != null && target.isAlive() && swordReaches(target)) {
            HorsemanCombat.swing(this, target);
        }
    }

    /** A swing at everyone within reach, each with its own attack roll. */
    public void startSweep() {
        sweepCooldown = SWEEP_COOLDOWN;
        swingCooldown = SWING_COOLDOWN;
        pendingSweep = isPassenger() ? SWEEP_IMPACT_MOUNTED : SWEEP_IMPACT_FOOT;
        crowding.clear();
        triggerAnim(ACTIONS, isPassenger() ? SWEEP_MOUNTED : SWEEP);
        HorsemanSounds.play(this, HorsemanSounds.GROWL);
    }

    public boolean isSweeping() {
        return pendingSweep > 0;
    }

    private void landSweep() {
        boolean burning = !isHeadless() && CritfallHorseman.config().fireDamage();
        HorsemanSounds.play(this, burning ? HorsemanSounds.SWORD_FIRE : HorsemanSounds.IMPACT, 2.0F);
        if (burning) {
            Effects.fireRing(this, SWORD_REACH);
        }
        for (LivingEntity victim : level().getEntitiesOfClass(
                        LivingEntity.class,
                        getBoundingBox().inflate(SWORD_REACH + 1.0, 2.0, SWORD_REACH + 1.0),
                        this::canSweep)) {
            HorsemanCombat.swing(this, victim);
        }
    }

    private boolean canSweep(LivingEntity victim) {
        return victim != this
                && victim != getVehicle()
                && victim.isAlive()
                && !(victim instanceof Horseman)
                && !(victim instanceof GallowsSteed)
                && !(victim instanceof Player player && (player.isCreative() || player.isSpectator()))
                && swordReaches(victim);
    }

    /**
     * Counts ticks each nearby player (or his target) spends close to him or behind him, decaying when they step clear.
     * Anyone reaching {@link #SWEEP_AFTER} triggers a sweep.
     */
    private void watchCrowding(ServerLevel level) {
        if (!CritfallHorseman.config().sweep() || isLeaving()) {
            crowding.clear();
            return;
        }
        Vec3 facing = Vec3.directionFromRotation(
                0.0F, getVehicle() != null ? getVehicle().getYRot() : yBodyRot);
        boolean sweep = false;
        for (LivingEntity near : level.getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox().inflate(SWORD_REACH + 1.0, 2.0, SWORD_REACH + 1.0),
                this::canSweep)) {
            if (!(near instanceof Player) && near != getTarget()) {
                continue;
            }
            Vec3 to = near.position().subtract(position()).multiply(1, 0, 1);
            boolean close = to.length() <= CROWD_DISTANCE;
            boolean behind = to.lengthSqr() > 1.0E-4 && to.normalize().dot(facing) < BEHIND;
            int ticks = crowding.getOrDefault(near.getUUID(), 0);
            ticks = close || behind ? ticks + 1 : Math.max(0, ticks - 2);
            crowding.put(near.getUUID(), ticks);
            sweep |= ticks >= SWEEP_AFTER;
        }
        if (sweep && sweepCooldown == 0 && pendingSwing == 0 && pendingSweep == 0 && !isThrowing()) {
            startSweep();
        }
    }

    /** Laughs and says the kill line when a nearby player dies, whatever killed them. */
    private void watchForDeaths(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) > Lines.RANGE * Lines.RANGE || player.isSpectator()) {
                continue;
            }
            if (player.isDeadOrDying()) {
                if (mourned.add(player.getUUID())) {
                    HorsemanSounds.laugh(this, false);
                    say(Lines.Line.KILL);
                }
            } else {
                mourned.remove(player.getUUID());
            }
        }
    }

    public boolean canCharge() {
        return CritfallHorseman.config().charge() && steed() != null && chargeCooldown == 0 && !isHeadless();
    }

    /** Charges through {@code target} to a point 5 blocks past them. */
    public void startCharge(LivingEntity target) {
        Vec3 through = target.position().subtract(position()).multiply(1, 0, 1);
        through = through.lengthSqr() < 1.0E-4 ? getLookAngle() : through.normalize();
        chargeTo = target.position().add(through.scale(5.0));
        chargeTicks = 0;
        trampled.clear();
        setCharging(true);
        HorsemanSounds.play(this, HorsemanSounds.HOOFBEATS);
        HorsemanSounds.play(this, HorsemanSounds.GROWL);
    }

    private void endCharge() {
        chargeTo = null;
        setCharging(false);
        chargeCooldown = CHARGE_COOLDOWN;
    }

    private void trampleTick(ServerLevel level) {
        Entity body = getVehicle() != null ? getVehicle() : this;
        for (LivingEntity victim : level.getEntitiesOfClass(
                LivingEntity.class, body.getBoundingBox().inflate(0.4), this::canTrample)) {
            trampled.put(victim.getUUID(), tickCount);
            HorsemanSounds.play(victim, HorsemanSounds.IMPACT, 1.2F);
            Effects.impact(victim);
            HorsemanCombat.trample(victim, body);
        }
        if (chargeTo != null) {
            chargeTicks++;
            if (body.position().multiply(1, 0, 1).distanceTo(chargeTo.multiply(1, 0, 1)) < 2.0
                    || chargeTicks > CHARGE_MAX_TICKS) {
                endCharge();
            }
        }
    }

    private boolean canTrample(LivingEntity victim) {
        Integer at = trampled.get(victim.getUUID());
        return victim != this
                && victim != getVehicle()
                && victim.isAlive()
                && !(victim instanceof Horseman)
                && !(victim instanceof GallowsSteed)
                && !(victim instanceof Player player && (player.isCreative() || player.isSpectator()))
                && (at == null || tickCount - at > TRAMPLE_AGAIN_TICKS);
    }

    /** Applies the charge to {@code victim} directly, as a charge passing through them would. */
    public void trample(LivingEntity victim) {
        trampled.put(victim.getUUID(), tickCount);
        Effects.impact(victim);
        HorsemanCombat.trample(victim, getVehicle() != null ? getVehicle() : this);
    }

    public boolean canThrow() {
        return CritfallHorseman.config().headThrow() && !isHeadless() && throwCooldown == 0 && pendingThrow == 0;
    }

    /** Starts the throw animation; the head is released {@link #THROW_RELEASE} ticks later. */
    public void startThrow(Vec3 at) {
        throwAt = at;
        pendingThrow = THROW_RELEASE;
        triggerAnim(ACTIONS, THROW_HEAD);
    }

    public boolean isThrowing() {
        return pendingThrow > 0;
    }

    /** Releases the head towards {@code at} immediately. */
    public void throwHead(Vec3 at) {
        if (isHeadless() || !(level() instanceof ServerLevel)) {
            return;
        }
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 hand = position().add(Mth.cos(yaw) * 0.6, isPassenger() ? 1.3 : 1.1, Mth.sin(yaw) * 0.6);
        ThrownHead thrown = new ThrownHead(level(), this, Head.of(this), true);
        thrown.setPos(hand);
        Vec3 aim = at.subtract(hand);
        thrown.shoot(aim.x, aim.y + aim.horizontalDistance() * 0.02, aim.z, 1.6F, 0.0F);
        level().addFreshEntity(thrown);
        setHeadless(true);
        whereabouts = new Head.Whereabouts(thrown.position(), thrown, null);
        lastCarrier = null;
        headMissingTicks = 0;
        fireLine.clear();
        lastFireAt = null;
        HorsemanSounds.play(this, HorsemanSounds.HEAD_WHOOSH, 2.0F);
        HorsemanSounds.play(this, HorsemanSounds.ROAR);
        Effects.neckFlare(this);
        say(Lines.Line.HEADLESS);
    }

    /** Called by the thrown head each tick to extend the line of fire on the ground below it. */
    public void leaveFire(Vec3 at) {
        // The head moves more than a block per tick, so fill in the points between ticks.
        Vec3 from = lastFireAt == null ? at : lastFireAt;
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(at) / FIRE_LINE_SPACING));
        for (int i = 1; i <= steps; i++) {
            fireOnGround(from.lerp(at, i / (double) steps));
        }
        lastFireAt = at;
    }

    private void fireOnGround(Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        for (int i = 0; i < 5; i++, pos = pos.below()) {
            if (!level().getBlockState(pos.below())
                    .getCollisionShape(level(), pos.below())
                    .isEmpty()) {
                if (!level().getFluidState(pos).isEmpty()) {
                    return;
                }
                Vec3 ground = new Vec3(at.x, pos.getY(), at.z);
                if (fireLine.isEmpty()
                        || fireLine.get(fireLine.size() - 1).distanceToSqr(ground)
                                > FIRE_LINE_SPACING * FIRE_LINE_SPACING * 0.8) {
                    fireLine.add(ground);
                }
                fireLineTicks = FIRE_LINE_TICKS;
                return;
            }
        }
    }

    private void burnFireLine() {
        if (fireLineTicks <= 0) {
            return;
        }
        if (--fireLineTicks == 0) {
            fireLine.clear();
            return;
        }
        if (tickCount % 2 == 0) {
            for (Vec3 point : fireLine) {
                Effects.groundFire(this, point);
            }
        }
    }

    /** While headless: finds the head, targets whoever carries it, and takes it back when in reach. */
    private void trackHead(ServerLevel level) {
        if (!isHeadless()) {
            return;
        }
        if (tickCount % 5 == 0) {
            whereabouts = Head.find(this, level);
        }
        if (whereabouts == null) {
            if (recallTicks > 0) {
                return;
            }
            if (++headMissingTicks > HEAD_LOST_TICKS) {
                takeHeadBack();
            }
            return;
        }
        headMissingTicks = 0;
        ServerPlayer carrier = whereabouts.carrier();
        if (carrier != null) {
            if (!carrier.getUUID().equals(lastCarrier)) {
                lastCarrier = carrier.getUUID();
                say(Lines.Line.CHASE);
            }
            setTarget(carrier);
            if (tickCount % 4 == 0) {
                Effects.burningHead(carrier, carrier.position().add(0, 0.8, 0));
            }
            if (reaches(carrier)) {
                Head.takeFrom(carrier, this);
                takeHeadBack();
            }
        } else if (whereabouts.entity() instanceof ItemEntity item) {
            if (item.isInWater() || item.isInLava()) {
                Effects.steam(item, item.position());
                HorsemanSounds.play(item, HorsemanSounds.HISS, 2.0F);
                item.discard();
                whereabouts = null;
                recallHead(item.position().add(0, 1.0, 0));
                return;
            }
            if (tickCount % 4 == 0) {
                Effects.burningHead(item, item.position());
            }
            if (random.nextInt(160) == 0) {
                HorsemanSounds.play(item, HorsemanSounds.CACKLE, 1.6F);
            }
            if (reaches(item)) {
                takeHeadBack();
            }
        }
    }

    /** The head flies back to him from {@code from} after {@link #HEAD_RETURN_TICKS}. */
    public void recallHead(Vec3 from) {
        if (recallTicks > 0) {
            return;
        }
        recallFrom = from;
        recallTicks = HEAD_RETURN_TICKS;
        headMissingTicks = 0;
    }

    public boolean recallingHead() {
        return recallTicks > 0;
    }

    /** Called each tick he fails to path to the head on the ground; recalls it after {@link #HEAD_UNREACHABLE_TICKS}. */
    void cannotReachHead(ItemEntity item) {
        if (++headUnreachableTicks > HEAD_UNREACHABLE_TICKS) {
            headUnreachableTicks = 0;
            Effects.smokeOut(item, item.position());
            item.discard();
            whereabouts = null;
            recallHead(item.position().add(0, 1.0, 0));
        }
    }

    /** Restores the head, wherever it was, and removes any copies of it from the world. */
    public void takeHeadBack() {
        if (!isHeadless()) {
            return;
        }
        if (level() instanceof ServerLevel server) {
            Head.putOut(this, server);
        }
        headGeneration++;
        recallTicks = -1;
        recallFrom = null;
        headUnreachableTicks = 0;
        setHeadless(false);
        setCharging(false);
        whereabouts = null;
        lastCarrier = null;
        headMissingTicks = 0;
        throwCooldown = THROW_COOLDOWN;
        Effects.retake(this);
        HorsemanSounds.play(this, HorsemanSounds.FIRE_ROAR, 2.0F);
        HorsemanSounds.laugh(this, false);
        say(Lines.Line.RETAKE);
    }

    /** Called by the steed when it dies under him. */
    public void onSteedLost() {
        stopRiding();
        setCharging(false);
        chargeTo = null;
        say(Lines.Line.STEED);
        HorsemanSounds.play(this, HorsemanSounds.GROWL);
    }

    /**
     * Shows {@code line} to nearby players. Lines that can repeat are limited to one per {@link #LINE_COOLDOWN} ticks
     * each. Returns whether it was shown.
     */
    public boolean say(Lines.Line line) {
        if (!line.once()) {
            Integer last = lastSaid.get(line);
            if (last != null && tickCount - last < LINE_COOLDOWN) {
                return false;
            }
            lastSaid.put(line, tickCount);
        }
        Lines.say(this, line);
        return true;
    }

    private void updateBossBars(ServerLevel level) {
        bossBar.setProgress(getHealth() / getMaxHealth());
        GallowsSteed steed = steed();
        if (steed != null) {
            steedBar.setProgress(steed.getHealth() / steed.getMaxHealth());
        }
        if (tickCount % 20 != 0) {
            return;
        }
        boolean on = CritfallHorseman.config().bossBar();
        for (ServerPlayer player : level.players()) {
            boolean near = on && player.isAlive() && player.distanceToSqr(this) < BOSS_BAR_RANGE * BOSS_BAR_RANGE;
            if (near) {
                bossBar.addPlayer(player);
            } else {
                bossBar.removePlayer(player);
            }
            if (near && steed != null && steed.isAlive()) {
                steedBar.addPlayer(player);
            } else {
                steedBar.removePlayer(player);
            }
        }
    }

    /** Sunrise: he rides away and is removed after {@link #EXIT_TICKS}. */
    public void rideOff() {
        if (isLeaving()) {
            return;
        }
        exitTicks = EXIT_TICKS;
        setCharging(true);
        say(Lines.Line.SUNRISE);
        HorsemanSounds.laugh(this, true);
        HorsemanSounds.play(this, HorsemanSounds.HOOFBEATS);
    }

    private void vanish() {
        Effects.vanish(this);
        if (level() instanceof ServerLevel server) {
            Head.putOut(this, server);
        }
        Entity steed = getVehicle();
        stopRiding();
        if (steed != null) {
            steed.discard();
        }
        discard();
    }

    @Override
    public void die(DamageSource source) {
        boolean wasAlive = !isRemoved() && !dead;
        GallowsSteed steed = steed();
        super.die(source);
        if (!wasAlive || !(level() instanceof ServerLevel server)) {
            return;
        }
        HorsemanSounds.play(this, HorsemanSounds.SCREAM, 4.0F);
        setCharging(false);
        if (steed != null) {
            stopRiding();
            steed.kill();
        }
        Head.putOut(this, server);
        bossBar.removeAllPlayers();
        steedBar.removeAllPlayers();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (CritfallHorseman.config().drops()) {
            spawnAtLocation(new ItemStack(HorsemanItems.EXECUTIONERS_SWORD));
            spawnAtLocation(new ItemStack(HorsemanItems.HORSEMAN_HEAD));
        }
    }

    /** Waits for the full death animation instead of vanilla's 20 ticks. */
    @Override
    protected void tickDeath() {
        if (++deathTime >= DEATH_TICKS && !level().isClientSide && !isRemoved()) {
            Effects.horsemanCrumbles(this);
            HorsemanSounds.play(this, HorsemanSounds.CRUMBLE, 2.0F);
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossBar.removeAllPlayers();
        steedBar.removeAllPlayers();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.FIRE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_DAMAGE;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Headless", isHeadless());
        tag.putInt("HeadGeneration", headGeneration);
        tag.putBoolean("ArrivedAtNight", arrivedAtNight);
        tag.putInt("ThrowCooldown", throwCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setHeadless(tag.getBoolean("Headless"));
        headGeneration = tag.getInt("HeadGeneration");
        arrivedAtNight = tag.getBoolean("ArrivedAtNight");
        throwCooldown = tag.getInt("ThrowCooldown");
    }

    /** Ambient particles: neck smoke, embers, the head's flame, and the charge trail. */
    private void clientEffects() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 neck = position().add(forward.scale(0.05)).add(0, 2.05, 0);
        if (random.nextFloat() < 0.7F) {
            level().addParticle(
                            ParticleTypes.LARGE_SMOKE,
                            neck.x + (random.nextDouble() - 0.5) * 0.15,
                            neck.y,
                            neck.z + (random.nextDouble() - 0.5) * 0.15,
                            0.0,
                            0.04,
                            0.0);
        }
        if (random.nextFloat() < (isHeadless() ? 0.8F : 0.15F)) {
            level().addParticle(
                            isHeadless() ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                            neck.x,
                            neck.y,
                            neck.z,
                            (random.nextDouble() - 0.5) * 0.04,
                            0.05,
                            (random.nextDouble() - 0.5) * 0.04);
        }
        if (random.nextFloat() < 0.12F) {
            level().addParticle(
                            ParticleTypes.SMALL_FLAME,
                            getRandomX(0.6),
                            getRandomY(),
                            getRandomZ(0.6),
                            (random.nextDouble() - 0.5) * 0.02,
                            0.03,
                            (random.nextDouble() - 0.5) * 0.02);
        }
        if (!isHeadless() && !isDeadOrDying()) {
            Vec3 head = position().add(left.scale(0.55)).add(0, isPassenger() ? 0.45 : 0.3, 0);
            Vec3 top = head.add(0, 0.6, 0);
            if (random.nextFloat() < 0.5F) {
                level().addParticle(
                                ParticleTypes.FLAME,
                                top.x + (random.nextDouble() - 0.5) * 0.2,
                                top.y,
                                top.z + (random.nextDouble() - 0.5) * 0.2,
                                0.0,
                                0.04 + random.nextDouble() * 0.03,
                                0.0);
            }
            Vec3 face = head.add(forward.scale(0.3)).add(0, 0.25, 0);
            if (random.nextFloat() < 0.12F) {
                level().addParticle(
                                ParticleTypes.SMALL_FLAME,
                                face.x,
                                face.y,
                                face.z,
                                forward.x * 0.02,
                                0.02,
                                forward.z * 0.02);
            }
            if (random.nextFloat() < 0.15F) {
                level().addParticle(
                                ParticleTypes.SMOKE,
                                face.x,
                                face.y + 0.1,
                                face.z,
                                forward.x * 0.01,
                                0.015,
                                forward.z * 0.01);
            }
            if (random.nextFloat() < 0.04F) {
                level().addParticle(ParticleTypes.LAVA, top.x, top.y, top.z, 0, 0, 0);
            }
        }
        if (isCharging() && getVehicle() != null) {
            Vec3 behind = getVehicle().position().subtract(forward.scale(1.4)).add(0, 0.6, 0);
            for (int i = 0; i < 2; i++) {
                level().addParticle(
                                ParticleTypes.FLAME,
                                behind.x + (random.nextDouble() - 0.5) * 0.8,
                                behind.y + random.nextDouble() * 0.8,
                                behind.z + (random.nextDouble() - 0.5) * 0.8,
                                0,
                                0.02,
                                0);
            }
            level().addParticle(ParticleTypes.SMOKE, behind.x, behind.y + 0.4, behind.z, 0, 0.03, 0);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 4, state -> {
            if (isDeadOrDying()) {
                return state.setAndContinue(DEATH);
            }
            if (isPassenger()) {
                return state.setAndContinue(isCharging() ? CHARGE : RIDE);
            }
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "reach", 6, state -> {
            if (isHeadless() && !isDeadOrDying()) {
                return state.setAndContinue(REACH);
            }
            return PlayState.STOP;
        }));
        controllers.add(new AnimationController<>(this, ACTIONS, 2, state -> PlayState.STOP)
                .triggerableAnim(SWING, RawAnimation.begin().thenPlay(SWING))
                .triggerableAnim(SWING_FOOT, RawAnimation.begin().thenPlay(SWING_FOOT))
                .triggerableAnim(THROW_HEAD, RawAnimation.begin().thenPlay(THROW_HEAD))
                .triggerableAnim(SWEEP, RawAnimation.begin().thenPlay(SWEEP))
                .triggerableAnim(SWEEP_MOUNTED, RawAnimation.begin().thenPlay(SWEEP_MOUNTED)));
        controllers.add(new AnimationController<>(this, "fire", 0, state -> state.setAndContinue(HEAD_FIRE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /**
     * All of his combat movement. When mounted, {@code getNavigation()} returns the steed's navigation, so this goal
     * steers the steed.
     */
    final class FightGoal extends Goal {

        /** A point to ride away to, to get room for a charge when the target can't be reached. */
        @Nullable
        private Vec3 backOff;

        private int backOffTicks;

        FightGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return isAlive()
                    && (isLeaving()
                            || isHeadless() && whereabouts != null
                            || getTarget() != null && getTarget().isAlive());
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            getNavigation().stop();
            if (!isHeadless() && chargeTo == null && !isLeaving()) {
                setCharging(false);
            }
        }

        @Override
        public void tick() {
            if (isLeaving()) {
                rideAway();
                return;
            }
            if (isHeadless()) {
                chaseHead();
                return;
            }
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (isThrowing()) {
                getNavigation().stop();
                return;
            }
            if (chargeTo != null) {
                getNavigation().moveTo(chargeTo.x, chargeTo.y, chargeTo.z, CHARGE_SPEED);
                return;
            }
            double distance = distanceTo(target);
            boolean sees = getSensing().hasLineOfSight(target);
            if (canCharge() && sees && distance >= 7.0 && distance <= 28.0) {
                backOff = null;
                startCharge(target);
                return;
            }
            if (canThrow() && sees && distance >= 6.0 && distance <= 22.0) {
                startThrow(target.getEyePosition());
                return;
            }
            if (backOff != null) {
                if (++backOffTicks > 60 || distance >= 9.0) {
                    backOff = null;
                } else {
                    getNavigation().moveTo(backOff.x, backOff.y, backOff.z, RIDE_SPEED);
                    return;
                }
            }
            if (!isPassenger()) {
                double dx = target.getX() - getX();
                double dz = target.getZ() - getZ();
                float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
                float yaw = Mth.approachDegrees(getYRot(), toward, TURN_SPEED);
                setYRot(yaw);
                yBodyRot = yaw;
                yHeadRot = yaw;
            }
            if (swordReaches(target)) {
                getNavigation().stop();
                if (swingCooldown == 0 && pendingSwing == 0 && pendingSweep == 0) {
                    startSwing();
                }
                return;
            }
            getNavigation().moveTo(target, isPassenger() ? RIDE_SPEED : WALK_SPEED);
            if (getNavigation().isDone() || getNavigation().isStuck()) {
                // Can't path to the target: throw over the obstacle, or back off to charge.
                if (canThrow() && sees && distance >= 3.0) {
                    startThrow(target.getEyePosition());
                } else if (steed() != null && CritfallHorseman.config().charge()) {
                    Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1.0E-4 ? getLookAngle().reverse() : away.normalize();
                    backOff = position().add(away.scale(10.0));
                    backOffTicks = 0;
                }
            }
        }

        private void chaseHead() {
            Head.Whereabouts head = whereabouts;
            if (head == null) {
                return;
            }
            Vec3 to = head.carrier() != null ? head.carrier().position() : head.position();
            if (isPassenger()) {
                setCharging(true);
            }
            getLookControl().setLookAt(to.x, to.y + 1.0, to.z);
            boolean pathed = getNavigation().moveTo(to.x, to.y, to.z, isPassenger() ? CHARGE_SPEED : RUN_SPEED);
            if (head.entity() instanceof ItemEntity item) {
                if (!reaches(item)
                        && (!pathed
                                || getNavigation().isDone()
                                || getNavigation().isStuck())) {
                    cannotReachHead(item);
                } else {
                    headUnreachableTicks = 0;
                }
            }
        }

        private void rideAway() {
            Player nearest = level().getNearestPlayer(Horseman.this, 64.0);
            Vec3 away = nearest == null
                    ? getLookAngle()
                    : position().subtract(nearest.position()).multiply(1, 0, 1).normalize();
            Vec3 to = position().add(away.scale(16.0));
            getNavigation().moveTo(to.x, to.y, to.z, CHARGE_SPEED);
        }
    }
}
