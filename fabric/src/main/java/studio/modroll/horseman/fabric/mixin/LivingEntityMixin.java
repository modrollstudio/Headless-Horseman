package studio.modroll.horseman.fabric.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.modroll.horseman.effect.HorsemanEffects;

/** Wraithwalk: the creature neither pushes others nor is pushed, so it passes through them. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void critfallHorseman$notPushed(CallbackInfoReturnable<Boolean> cir) {
        if (HorsemanEffects.passesThrough((LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void critfallHorseman$pushesNothing(CallbackInfo ci) {
        if (HorsemanEffects.passesThrough((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
