package studio.modroll.horseman.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.fight.Effects;
import studio.modroll.horseman.fight.Head;
import studio.modroll.horseman.fight.HorsemanCombat;
import studio.modroll.horseman.item.HorsemanItems;
import studio.modroll.horseman.sound.HorsemanSounds;

/**
 * The head in flight, in one of three modes:
 *
 * <ul>
 *   <li>thrown by the Horseman: flies nearly flat, and everything it passes makes a Dexterity save against the line of
 *       fire;
 *   <li>thrown by a player: a short pass that the player it hits catches;
 *   <li>returning: flies back to the Horseman's hand through blocks.
 * </ul>
 *
 * <p>On landing it becomes an item entity, unless it lands in water or lava, in which case it is put out and recalled.
 */
public class ThrownHead extends ThrowableItemProjectile {

    public static final double BURN_REACH = 1.3;
    /** Blocks per tick. */
    public static final double RETURN_SPEED = 0.8;

    private static final EntityDataAccessor<Boolean> RETURNING =
            SynchedEntityData.defineId(ThrownHead.class, EntityDataSerializers.BOOLEAN);

    private boolean byHorseman;
    private final Set<UUID> burnt = new HashSet<>();

    public ThrownHead(EntityType<? extends ThrownHead> type, Level level) {
        super(type, level);
    }

    public ThrownHead(Level level, LivingEntity thrower, ItemStack head, boolean byHorseman) {
        super(HorsemanEntities.THROWN_HEAD, thrower, level);
        setItem(head);
        this.byHorseman = byHorseman;
    }

    public static ThrownHead flyingBack(Horseman horseman, Vec3 from) {
        ThrownHead head = new ThrownHead(horseman.level(), horseman, Head.of(horseman), false);
        head.setPos(from);
        head.entityData.set(RETURNING, true);
        head.noPhysics = true;
        head.setNoGravity(true);
        return head;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RETURNING, false);
    }

    @Override
    protected Item getDefaultItem() {
        return HorsemanItems.BURNING_HEAD;
    }

    @Override
    protected double getDefaultGravity() {
        return byHorseman ? 0.01 : 0.05;
    }

    public boolean returning() {
        return entityData.get(RETURNING);
    }

    @Override
    public void tick() {
        if (returning()) {
            noPhysics = true;
            setNoGravity(true);
        }
        super.tick();
        if (level().isClientSide) {
            trail();
            return;
        }
        if (!isAlive()) {
            return;
        }
        if (returning()) {
            flyBack();
            return;
        }
        if (isInWater() || isInLava()) {
            douse(position());
            return;
        }
        if (byHorseman) {
            Horseman horseman = horseman();
            if (horseman != null) {
                horseman.leaveFire(position());
            }
            for (LivingEntity victim : level().getEntitiesOfClass(
                            LivingEntity.class, getBoundingBox().inflate(BURN_REACH), this::canBurn)) {
                burnt.add(victim.getUUID());
                HorsemanCombat.burn(victim);
            }
        }
    }

    private void flyBack() {
        Horseman horseman = horseman();
        if (horseman == null || !horseman.isAlive() || !horseman.isHeadless()) {
            discard();
            return;
        }
        Vec3 hand = horseman.position().add(0, horseman.isPassenger() ? 1.3 : 1.1, 0);
        Vec3 to = hand.subtract(position());
        if (to.length() < 1.5) {
            horseman.takeHeadBack();
            discard();
            return;
        }
        setDeltaMovement(to.normalize().scale(Math.min(RETURN_SPEED, to.length())));
    }

    private void douse(Vec3 at) {
        if (level() instanceof ServerLevel) {
            Effects.steam(this, at);
            HorsemanSounds.play(level(), at, HorsemanSounds.HISS, 2.0F);
            Horseman horseman = horseman();
            if (horseman != null) {
                horseman.recallHead(at.add(0, 1.0, 0));
            }
        }
        discard();
    }

    private boolean canBurn(LivingEntity victim) {
        return victim.isAlive()
                && !burnt.contains(victim.getUUID())
                && !(victim instanceof Horseman)
                && !(victim instanceof GallowsSteed)
                && !(victim instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    /** Client-side trail. His throws fill the whole path since the last tick, since the head moves fast. */
    private void trail() {
        if (!CritfallHorseman.clientConfig().ambientParticles()) {
            return;
        }
        Vec3 now = position().add(0, 0.25, 0);
        Vec3 then = new Vec3(xo, yo + 0.25, zo);
        if (returning()) {
            level().addParticle(ParticleTypes.SMOKE, now.x, now.y, now.z, 0.0, 0.02, 0.0);
            if (random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.SMALL_FLAME, now.x, now.y, now.z, 0.0, 0.01, 0.0);
            }
            return;
        }
        int steps = byHorseman || getDeltaMovement().lengthSqr() > 1.0 ? 8 : 3;
        for (int i = 0; i < steps; i++) {
            Vec3 at = then.lerp(now, i / (double) steps);
            double sx = (random.nextDouble() - 0.5) * 0.4;
            double sy = (random.nextDouble() - 0.5) * 0.4;
            double sz = (random.nextDouble() - 0.5) * 0.4;
            level().addParticle(ParticleTypes.FLAME, at.x + sx, at.y + sy, at.z + sz, 0.0, 0.03, 0.0);
            if (i % 2 == 0) {
                level().addParticle(ParticleTypes.LARGE_SMOKE, at.x + sx, at.y + 0.2, at.z + sz, 0.0, 0.04, 0.0);
            }
            if (random.nextInt(4) == 0) {
                level().addParticle(ParticleTypes.LAVA, at.x, at.y, at.z, 0.0, 0.0, 0.0);
            }
        }
        level().addParticle(ParticleTypes.FLAME, now.x, now.y + 0.3, now.z, 0.0, 0.08, 0.0);
    }

    private Horseman horseman() {
        UUID owner = Head.owner(getItem());
        return owner != null && level() instanceof ServerLevel server && server.getEntity(owner) instanceof Horseman h
                ? h
                : null;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (returning() || target instanceof GallowsSteed) {
            return false;
        }
        if (target instanceof Horseman) {
            return !byHorseman; // a player can throw it back to him
        }
        return super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide || returning()) {
            return;
        }
        Entity target = hit.getEntity();
        if (target instanceof Horseman horseman && Head.isHeadOf(getItem(), horseman)) {
            horseman.takeHeadBack();
            discard();
            return;
        }
        if (!byHorseman
                && target instanceof Player catcher
                && catcher.getInventory().add(getItem().copy())) {
            discard();
            return;
        }
        land(position());
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (returning()) {
            return;
        }
        super.onHitBlock(hit);
        if (!level().isClientSide) {
            Vec3 face = Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.3);
            land(hit.getLocation().add(face));
        }
    }

    /** Drops the head as an item entity, or puts it out if it landed in a fluid. */
    private void land(Vec3 at) {
        if (!isAlive()) {
            return;
        }
        if (!level().getFluidState(BlockPos.containing(at)).isEmpty()) {
            douse(at);
            return;
        }
        if (byHorseman) {
            Effects.headLands(this, at);
            HorsemanSounds.play(level(), at, HorsemanSounds.FIRE_ROAR, 2.0F);
        }
        ItemEntity item = new ItemEntity(level(), at.x, at.y, at.z, getItem().copy(), 0.0, 0.1, 0.0);
        item.setUnlimitedLifetime();
        item.setPickUpDelay(byHorseman ? 20 : 10);
        if (!CritfallHorseman.config().headPassing()) {
            item.setNeverPickUp();
        }
        level().addFreshEntity(item);
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("ByHorseman", byHorseman);
        tag.putBoolean("Returning", returning());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        byHorseman = tag.getBoolean("ByHorseman");
        entityData.set(RETURNING, tag.getBoolean("Returning"));
    }
}
