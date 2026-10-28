package studio.modroll.horseman.fight;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.HazardSave;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.combat.HazardSaveResult;
import studio.modroll.critfall.api.combat.SuccessDamage;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.entity.Horseman;
import studio.modroll.horseman.entity.HorsemanStats;

/**
 * All of the Horseman's dice, made through Critfall: the sword's attack roll and damage, and the two Dexterity saves,
 * each with its own hazard id.
 */
public final class HorsemanCombat {

    public static final ResourceLocation CHARGE = CritfallHorseman.id("charge");
    public static final ResourceLocation HEAD_THROW = CritfallHorseman.id("head_throw");

    public static final String CHARGE_LABEL = "hazard.critfall_horseman.charge";
    public static final String HEAD_THROW_LABEL = "hazard.critfall_horseman.head_throw";

    /** Used if his Critfall profile is missing; matches the shipped profile. */
    public static final DiceExpression FALLBACK_MELEE = DiceExpression.parse("2d4");

    public static final int KNOCKED_DOWN_TICKS = 50;

    public static final ResourceKey<DamageType> TRAMPLE = damageType("trample");
    public static final ResourceKey<DamageType> HEAD_FIRE = damageType("head_fire");

    private HorsemanCombat() {}

    private static HorsemanStats stats(Entity entity) {
        return entity.level() instanceof ServerLevel level
                ? HorsemanStats.of(level, "horseman")
                : HorsemanStats.HORSEMAN;
    }

    private static ResourceKey<DamageType> damageType(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, CritfallHorseman.id(name));
    }

    /**
     * One sword attack. While he has his head the fire dice are added to the same roll rather than dealt as a separate
     * hit, because Initiative cancels non-driven damage between encounter participants. Returns whether it hit.
     */
    public static boolean swing(Horseman horseman, LivingEntity target) {
        AttackContext context = AttackContext.melee(horseman.damageSources().mobAttack(horseman), ItemStack.EMPTY);
        if (!horseman.isHeadless() && CritfallHorseman.config().fireDamage()) {
            context = context.withDamageDice(burningSwordDice(horseman));
        }
        return RollService.performAttack(horseman, target, context).isHit();
    }

    /** His profile's melee dice plus the blade fire dice. */
    public static DiceExpression burningSwordDice(Horseman horseman) {
        DiceExpression melee =
                RollService.effectiveEntity(horseman).meleeDamage().orElse(FALLBACK_MELEE);
        return DiceExpression.parse(melee + "+" + stats(horseman).bladeFire());
    }

    /** Dexterity save against the charge; on a failure the victim is also knocked away from {@code from}. */
    public static boolean trample(LivingEntity victim, Entity from) {
        HorsemanStats stats = stats(victim);
        HazardSave save = new HazardSave(
                        CHARGE_LABEL,
                        TRAMPLE,
                        ModifierProvider.DEXTERITY_SAVE,
                        stats.chargeDc(),
                        stats.chargeDamage(),
                        SuccessDamage.HALF,
                        RollMode.NORMAL)
                .withId(CHARGE);
        HazardSaveResult result = RollService.performHazardSave(victim, save);
        if (!result.saved() && victim.isAlive()) {
            knockDown(victim, from);
        }
        return result.saved();
    }

    /** Dexterity save against the line of fire. */
    public static boolean burn(LivingEntity victim) {
        HorsemanStats stats = stats(victim);
        HazardSave save = new HazardSave(
                        HEAD_THROW_LABEL,
                        HEAD_FIRE,
                        ModifierProvider.DEXTERITY_SAVE,
                        stats.headThrowDc(),
                        stats.headThrowDamage(),
                        SuccessDamage.HALF,
                        RollMode.NORMAL)
                .withId(HEAD_THROW);
        return RollService.performHazardSave(victim, save).saved();
    }

    private static void knockDown(LivingEntity victim, Entity from) {
        Vec3 away = victim.position().subtract(from.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) {
            away = from.getLookAngle().multiply(1, 0, 1);
        }
        away = away.normalize();
        victim.knockback(1.4, -away.x, -away.z);
        victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.35, 0));
        victim.hurtMarked = true;
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, KNOCKED_DOWN_TICKS, 3, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, KNOCKED_DOWN_TICKS, 1, false, false));
    }
}
