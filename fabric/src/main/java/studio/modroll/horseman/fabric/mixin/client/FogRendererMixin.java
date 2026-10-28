package studio.modroll.horseman.fabric.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.modroll.horseman.client.Atmosphere;

/** Fabric has no fog event, so the Horseman's fog is applied after vanilla's setup. */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void critfall_horseman$fog(
            Camera camera,
            FogRenderer.FogMode mode,
            float renderDistance,
            boolean foggy,
            float partialTick,
            CallbackInfo info) {
        if (Atmosphere.foggy(partialTick)) {
            RenderSystem.setShaderFogStart(Atmosphere.fogStart(RenderSystem.getShaderFogStart(), partialTick));
            RenderSystem.setShaderFogEnd(Atmosphere.fogEnd(RenderSystem.getShaderFogEnd(), partialTick));
        }
    }
}
