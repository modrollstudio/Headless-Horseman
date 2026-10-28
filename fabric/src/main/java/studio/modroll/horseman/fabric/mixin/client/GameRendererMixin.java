package studio.modroll.horseman.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.modroll.horseman.client.Atmosphere;

/** Fabric has no camera-angle event, so the charge shake is applied where vanilla applies the hurt shake. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {

    @Inject(method = "bobHurt", at = @At("HEAD"))
    private void critfall_horseman$shake(PoseStack poseStack, float partialTick, CallbackInfo info) {
        if (Atmosphere.shake() > 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(Atmosphere.shakeRoll(partialTick)));
            poseStack.mulPose(Axis.XP.rotationDegrees(Atmosphere.shakePitch(partialTick)));
        }
    }
}
