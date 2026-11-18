package studio.modroll.horseman.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import studio.modroll.critfall.api.HazardSave;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.combat.SuccessDamage;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.sound.HorsemanSounds;

/**
 * The worn head as a lantern. Its light is a flag on the stack that dynamic light mods read from {@code
 * assets/critfall_horseman/dynamiclights/item/}, and that Depths can read through {@code #critfall_depths:carried_light};
 * no light block is ever placed. Once each in game day it breathes a cone of fire: a Dexterity save through Critfall
 * for half, with its own hazard id. The two keys reach the server as a {@link
 * studio.modroll.horseman.network.LanternPayload}.
 */
public final class Lantern {

    public static final ResourceLocation BREATH = CritfallHorseman.id("lantern_breath");
    public static final String BREATH_LABEL = "hazard.critfall_horseman.lantern_breath";
    public static final ResourceKey<DamageType> BREATH_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, CritfallHorseman.id("lantern_breath"));

    public static final String LIGHT_KEY = "key.critfall_horseman.lantern_light";
    public static final String BREATH_KEY = "key.critfall_horseman.lantern_breath";
    public static final String KEY_CATEGORY = "key.categories.critfall_horseman";

    public static final int BREATH_CHARGES = 1;

    private static final String LIT = "Lantern";

    private Lantern() {}

    public static boolean isLit(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getBoolean(LIT);
    }

    public static void setLit(ItemStack stack, boolean lit) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (lit) {
                tag.putBoolean(LIT, true);
            } else {
                tag.remove(LIT);
            }
        });
    }

    /** A lit head, as he drops it. */
    public static ItemStack litHead() {
        ItemStack stack = new ItemStack(HorsemanItems.HORSEMAN_HEAD);
        setLit(stack, true);
        return stack;
    }

    public static boolean lightAllowed() {
        return CritfallHorseman.config().enabled() && CritfallHorseman.config().lanternLight();
    }

    private static boolean wears(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(HorsemanItems.HORSEMAN_HEAD);
    }

    /** The light key: switches the worn head's light. */
    public static void switchLight(ServerPlayer player) {
        if (!wears(player) || !lightAllowed()) {
            return;
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        boolean lit = !isLit(head);
        setLit(head, lit);
        player.level()
                .playSound(
                        null,
                        player.getX(),
                        player.getEyeY(),
                        player.getZ(),
                        lit ? SoundEvents.FIRECHARGE_USE : SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.PLAYERS,
                        0.6F,
                        lit ? 1.4F : 1.2F);
    }

    /**
     * The breath key: once a day, a cone of fire from the worn head, credited to the player. Every living thing in it that the breath can reach
     * saves; other players only where PvP is on. Returns whether it breathed.
     */
    public static boolean breathe(ServerPlayer player) {
        if (!wears(player)
                || !CritfallHorseman.config().enabled()
                || !CritfallHorseman.config().lanternBreath()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!Charges.spend(head, level, BREATH_CHARGES)) {
            player.displayClientMessage(Component.translatable("item.critfall_horseman.horseman_head.spent"), true);
            return false;
        }
        ItemStats stats = ItemStats.of(level, "horseman_head");
        double range = stats.number("breath_range", 5.0, 1.0);
        double halfAngle = Math.toRadians(stats.number("breath_degrees", 53.0, 1.0) / 2.0);
        HazardSave save = new HazardSave(
                        BREATH_LABEL,
                        BREATH_DAMAGE,
                        ModifierProvider.DEXTERITY_SAVE,
                        stats.integer("breath_dc", 13, 1),
                        stats.dice("breath_damage", "3d6"),
                        SuccessDamage.HALF,
                        RollMode.NORMAL)
                .withId(BREATH)
                .withSource(player);
        for (LivingEntity target : inCone(player, range, halfAngle)) {
            RollService.performHazardSave(target, save);
        }
        breathEffects(player, range, halfAngle);
        return true;
    }

    /** Living things within {@code range} of the eyes and {@code halfAngle} of the look, with nothing solid between. */
    public static List<LivingEntity> inCone(ServerPlayer player, double range, double halfAngle) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity target : player.serverLevel()
                .getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range + 1.0))) {
            if (!Targets.canAffect(player, target)) {
                continue;
            }
            Vec3 centre = target.getBoundingBox().getCenter();
            Vec3 to = centre.subtract(eye);
            double distance = to.length();
            if (distance > range + target.getBbWidth() / 2.0 || distance < 1.0E-4) {
                continue;
            }
            if (Math.acos(Mth.clamp(to.normalize().dot(look), -1.0, 1.0)) > halfAngle) {
                continue;
            }
            HitResult wall = player.level()
                    .clip(new ClipContext(eye, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (wall.getType() == HitResult.Type.MISS) {
                out.add(target);
            }
        }
        return out;
    }

    private static void breathEffects(ServerPlayer player, double range, double halfAngle) {
        ServerLevel level = player.serverLevel();
        HorsemanSounds.play(player, HorsemanSounds.FIRE_ROAR, 1.5F);
        if (!CritfallHorseman.config().effects()) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (double d = 0.6; d <= range; d += 0.5) {
            double spread = Math.tan(halfAngle) * d * 0.5;
            Vec3 at = eye.add(look.scale(d));
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 6, spread, spread, spread, 0.02);
            level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 3, spread, spread, spread, 0.01);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, eye.x, eye.y, eye.z, 6, 0.2, 0.2, 0.2, 0.02);
    }
}
