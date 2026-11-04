package studio.modroll.horseman.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import studio.modroll.critfall.api.HazardSave;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.combat.SuccessDamage;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.effect.HorsemanEffects;
import studio.modroll.horseman.entity.GallowsRopes;

/**
 * The Last Hangman's Rope. Three charges a day: gallows ropes burst from the ground where the user aims, and everything
 * standing there makes a Strength save through Critfall or is bound in place for a few seconds.
 */
public class HangmansRopeItem extends Item {

    public static final ResourceLocation HAZARD = CritfallHorseman.id("hangmans_rope");
    public static final String LABEL = "hazard.critfall_horseman.hangmans_rope";
    public static final ResourceKey<DamageType> DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, CritfallHorseman.id("hangmans_rope"));

    public HangmansRopeItem(Properties properties) {
        super(properties);
    }

    private static ItemStats stats(ServerLevel level) {
        return ItemStats.of(level, "hangmans_rope");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!CritfallHorseman.config().enabled() || !CritfallHorseman.config().hangmansRope()) {
            return InteractionResultHolder.fail(stack);
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer user)) {
            return InteractionResultHolder.consume(stack);
        }
        ItemStats stats = stats(server);
        Vec3 at = aim(user, stats.number("range", 16.0, 1.0));
        if (at == null) {
            return InteractionResultHolder.fail(stack);
        }
        if (!Charges.spend(stack, server, stats.integer("charges", 3, 1))) {
            user.displayClientMessage(Component.translatable("item.critfall_horseman.charges.none"), true);
            return InteractionResultHolder.fail(stack);
        }
        bind(user, at);
        return InteractionResultHolder.success(stack);
    }

    /** The ground under the first block or creature the user looks at within {@code range}, or null. */
    @Nullable
    public static Vec3 aim(ServerPlayer user, double range) {
        Vec3 eye = user.getEyePosition();
        Vec3 end = eye.add(user.getLookAngle().scale(range));
        HitResult block =
                user.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        Vec3 reach = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(
                user.level(),
                user,
                eye,
                reach,
                new AABB(eye, reach).inflate(1.0),
                target -> target instanceof LivingEntity && target.isPickable() && !target.isSpectator());
        Vec3 hit;
        if (entity != null) {
            hit = entity.getEntity().position();
        } else if (block.getType() != HitResult.Type.MISS) {
            hit = block.getLocation();
        } else {
            return null;
        }
        return ground(user.level(), hit);
    }

    /** The top of the first solid block at or up to four blocks under {@code at}. */
    private static Vec3 ground(Level level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at.x, at.y + 0.01, at.z);
        for (int i = 0; i < 5; i++) {
            BlockPos below = pos.below(i);
            VoxelShape shape = level.getBlockState(below).getCollisionShape(level, below);
            if (!shape.isEmpty()) {
                return new Vec3(at.x, below.getY() + shape.max(Direction.Axis.Y), at.z);
            }
        }
        return at;
    }

    /** Ropes burst up at {@code at}; everything the user may catch within the radius saves or is bound. */
    public static void bind(ServerPlayer user, Vec3 at) {
        ServerLevel level = user.serverLevel();
        ItemStats stats = stats(level);
        double radius = stats.number("radius", 1.5, 0.5);
        int holdTicks = Math.round((float) stats.number("hold_seconds", 3.0, 0.5) * 20);
        HazardSave save = new HazardSave(
                        LABEL,
                        DAMAGE,
                        ModifierProvider.STRENGTH_SAVE,
                        stats.integer("dc", 13, 1),
                        HazardSave.NO_DAMAGE,
                        SuccessDamage.NONE,
                        RollMode.NORMAL)
                .withId(HAZARD)
                .withSource(user);
        AABB area = new AABB(at.x - radius, at.y - 1.0, at.z - radius, at.x + radius, at.y + 2.0, at.z + radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            double dx = target.getX() - at.x;
            double dz = target.getZ() - at.z;
            if (dx * dx + dz * dz > radius * radius || !Targets.canAffect(user, target)) {
                continue;
            }
            if (!RollService.performHazardSave(target, save).saved()) {
                target.addEffect(new MobEffectInstance(HorsemanEffects.bound(), holdTicks, 0, false, true, true));
            }
        }
        level.addFreshEntity(new GallowsRopes(level, at.x, at.y, at.z, holdTicks));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.0F, 0.7F);
        if (CritfallHorseman.config().effects()) {
            BlockPos under = BlockPos.containing(at.x, at.y - 0.5, at.z);
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(under)),
                    at.x,
                    at.y + 0.1,
                    at.z,
                    30,
                    radius * 0.5,
                    0.1,
                    radius * 0.5,
                    0.1);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (level instanceof ServerLevel server && holder.tickCount % 20 == 0) {
            Charges.refresh(stack, server, stats(server).integer("charges", 3, 1));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return Charges.barVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Charges.barWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Charges.barColour();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.critfall_horseman.hangmans_rope.lore")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.critfall_horseman.hangmans_rope.use")
                .withStyle(ChatFormatting.GRAY));
        Charges.tooltip(stack, lines);
    }
}
