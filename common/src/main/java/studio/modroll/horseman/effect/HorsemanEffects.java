package studio.modroll.horseman.effect;

import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import studio.modroll.horseman.CritfallHorseman;

/**
 * Mob effects from his rewards. Bound: held in place by the hangman's rope, no walking, flying or jumping. Wraithwalk:
 * from a treat, the wearer and other creatures pass through each other instead of pushing (the loaders' mixins ask
 * {@link #passesThrough}).
 */
public final class HorsemanEffects {

    public static final ResourceLocation BOUND_ID = CritfallHorseman.id("bound");
    public static final ResourceLocation WRAITHWALK_ID = CritfallHorseman.id("wraithwalk");

    public static final MobEffect BOUND = new Effect(MobEffectCategory.HARMFUL, 0x705C3A)
            .addAttributeModifier(
                    Attributes.MOVEMENT_SPEED, BOUND_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(
                    Attributes.FLYING_SPEED, BOUND_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(
                    Attributes.JUMP_STRENGTH, BOUND_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    public static final MobEffect WRAITHWALK = new Effect(MobEffectCategory.BENEFICIAL, 0xBFC6D9);

    private HorsemanEffects() {}

    /** MobEffect's constructor is protected. */
    private static final class Effect extends MobEffect {
        Effect(MobEffectCategory category, int colour) {
            super(category, colour);
        }
    }

    public static void register(BiConsumer<ResourceLocation, MobEffect> registrar) {
        registrar.accept(BOUND_ID, BOUND);
        registrar.accept(WRAITHWALK_ID, WRAITHWALK);
    }

    public static Holder<MobEffect> bound() {
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(BOUND);
    }

    public static Holder<MobEffect> wraithwalk() {
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(WRAITHWALK);
    }

    /** Whether {@code entity} neither pushes nor is pushed by other creatures. */
    public static boolean passesThrough(LivingEntity entity) {
        return entity.hasEffect(wraithwalk());
    }
}
