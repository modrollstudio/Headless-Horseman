package studio.modroll.horseman.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The ropes the hangman's rope calls out of the ground: only a sight, the hold itself is the {@code bound} effect. They
 * burst up, writhe for as long as the hold lasts, then sink and are gone. Never saved.
 */
public class GallowsRopes extends Entity implements GeoEntity {

    /** The sink animation's length. */
    public static final int SINK_TICKS = 8;

    private static final EntityDataAccessor<Integer> HOLD_TICKS =
            SynchedEntityData.defineId(GallowsRopes.class, EntityDataSerializers.INT);

    private static final RawAnimation BURST_AND_HOLD =
            RawAnimation.begin().thenPlay("burst").thenLoop("hold");
    private static final RawAnimation SINK = RawAnimation.begin().thenPlayAndHold("sink");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GallowsRopes(EntityType<? extends GallowsRopes> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public GallowsRopes(Level level, double x, double y, double z, int holdTicks) {
        this(HorsemanEntities.GALLOWS_ROPES, level);
        setPos(x, y, z);
        entityData.set(HOLD_TICKS, holdTicks);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(HOLD_TICKS, 60);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= entityData.get(HOLD_TICKS) + SINK_TICKS) {
            discard();
        }
    }

    public boolean sinking() {
        return tickCount >= entityData.get(HOLD_TICKS);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this, "ropes", 2, state -> state.setAndContinue(sinking() ? SINK : BURST_AND_HOLD)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
