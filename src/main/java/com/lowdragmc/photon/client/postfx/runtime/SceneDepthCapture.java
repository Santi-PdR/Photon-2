package com.lowdragmc.photon.client.postfx.runtime;

import com.lowdragmc.lowdraglib2.client.shader.HDRTarget;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;
import com.lowdragmc.photon.client.util.FramebufferState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/** Texture-backed scene-depth snapshot for post-effect invocations outside the particle pipeline. */
@OnlyIn(Dist.CLIENT)
public final class SceneDepthCapture {
    @Nullable
    private static HDRTarget target;

    private SceneDepthCapture() {}

    /** Copy {@code source}'s depth into a sampleable target, preserving the caller's GL target and viewport. */
    public static int capture(RenderTarget source) {
        RenderSystem.assertOnRenderThread();
        if (source.width <= 0 || source.height <= 0 || source.getDepthTextureId() <= 0) return -1;

        var framebufferState = FramebufferState.capture();
        int viewportX = GlStateManager.Viewport.x();
        int viewportY = GlStateManager.Viewport.y();
        int viewportWidth = GlStateManager.Viewport.width();
        int viewportHeight = GlStateManager.Viewport.height();
        try {
            target = RenderPassPipeline.resize(target, source.width, source.height, true);
            target.copyDepthFrom(source);
            int depthTexture = target.getDepthTextureId();
            return depthTexture > 0 ? depthTexture : -1;
        } finally {
            framebufferState.restore();
            RenderSystem.viewport(viewportX, viewportY, viewportWidth, viewportHeight);
        }
    }
}
