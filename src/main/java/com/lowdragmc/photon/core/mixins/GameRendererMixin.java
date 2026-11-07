package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.photon.client.light.DynamicLightRenderer;
import com.lowdragmc.photon.client.fx.FXPostProcessPreparation;
import com.lowdragmc.photon.client.fx.FXPreloader;
import com.lowdragmc.photon.client.postfx.PhotonPostFX;
import com.lowdragmc.photon.core.mixins.accessor.ParticleEngineAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The slot for compositing Photon's deferred FX layer, and for its custom post-effect chain.
 *
 * <p>Injecting <b>after the {@code LevelRenderer.renderLevel} call</b> rather than at either
 * method's RETURN/TAIL is deliberate: Iris runs its composite and final passes from a RETURN inject
 * inside that call, and its colour-space conversion from a TAIL inject on this method. Sitting on
 * the call site puts us squarely between the two with no dependence on mixin priority — a
 * RETURN/TAIL inject of our own would be racing Iris by priority number instead.
 *
 * <p>The same point is what {@code FXCompositeMode.LATE} needs on the plain path: it is past the
 * clouds and the weather in Fast/Fancy, and past {@code transparencyChain.process()} in Fabulous.
 *
 * <p>{@code RenderLevelStageEvent.AFTER_LEVEL} would not do: it fires inside {@code renderLevel},
 * before the pack's composites have run — and, in Fabulous, before the transparency chain has
 * resolved the five layer targets into the main one.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render(FJZ)V", at = @At("HEAD"))
    private void photon$frameStart(float partialTick, long finishTimeNano, boolean renderLevel, CallbackInfo ci) {
        FXPreloader.onFrameStart();
    }

    @Inject(method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V"))
    private void photon$prepareTimelinePostEffects(float partialTick, long finishTimeNano, PoseStack poseStack, CallbackInfo ci) {
        ParticleEngine engine = Minecraft.getInstance().particleEngine;
        FXPostProcessPreparation.prepare(((ParticleEngineAccessor) engine).getParticles(), partialTick);
    }

    @Inject(method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V", shift = At.Shift.AFTER))
    private void photon$afterLevelRender(float partialTick, long finishTimeNano, PoseStack poseStack, CallbackInfo ci) {
        DynamicLightRenderer.onLevelRendered();
        PhotonPostFX.onLevelRenderComplete();
        DynamicLightRenderer.onFrameComposited();
    }

    @Inject(method = "render(FJZ)V", at = @At("TAIL"))
    private void photon$frameEnd(float partialTick, long finishTimeNano, boolean renderLevel, CallbackInfo ci) {
        PhotonPostFX.onFrameEnd();
    }
}

