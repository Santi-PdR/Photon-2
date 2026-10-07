package com.lowdragmc.photon.client.postprocessing;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.client.shader.HDRTarget;
import com.lowdragmc.photon.PhotonConfig;
import com.lowdragmc.photon.client.PhotonShaders;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;
import com.lowdragmc.photon.client.postfx.runtime.SceneBlit;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL46;

import javax.annotation.Nullable;
import java.util.ArrayList;

@OnlyIn(Dist.CLIENT)
public class PhotonPostProcessing {
    private record TargetKey(int width, int height, int requestedLevels) {}

    private static final class TargetSet {
        final int width;
        final int height;
        final int requestedLevels;
        @Nullable final HDRTarget output;
        final ArrayList<HDRTarget> mips;

        TargetSet(int width, int height, int requestedLevels) {
            this.width = width;
            this.height = height;
            this.requestedLevels = requestedLevels;
            var sizes = BloomMipLayout.compute(width, height, requestedLevels);
            if (sizes.isEmpty()) {
                output = null;
                mips = new ArrayList<>();
                return;
            }
            HDRTarget allocatedOutput = null;
            var allocatedMips = new ArrayList<HDRTarget>(sizes.size());
            try {
                allocatedOutput = resize(null, width, height);
                for (var size : sizes) {
                    allocatedMips.add(resize(null, size.width(), size.height()));
                }
            } catch (RuntimeException | Error failure) {
                destroyAfterFailedBuild(allocatedOutput, failure);
                for (var mip : allocatedMips) destroyAfterFailedBuild(mip, failure);
                throw failure;
            }
            output = allocatedOutput;
            mips = allocatedMips;
        }

        void destroy() {
            var targets = new ArrayList<HDRTarget>(mips.size() + 1);
            if (output != null) targets.add(output);
            targets.addAll(mips);
            ResourceDisposal.disposeAll(targets, HDRTarget::destroyBuffers);
        }
    }

    private static final IdleFrameCache<TargetKey, TargetSet> TARGETS = new IdleFrameCache<>(120);
    private static final FrameRetryPolicy<TargetKey> FAILED_TARGET_RETRY = new FrameRetryPolicy<>();
    private static final int ALLOCATION_RETRY_FRAMES = 120;
    private static long frame;
    @Nullable private static TargetSet current;

    public static void prepareTarget(int width, int height) {
        int levels = PhotonConfig.INSTANCE.bloomMipLevel.get();
        var key = new TargetKey(width, height, levels);
        if (FAILED_TARGET_RETRY.shouldDefer(key, frame)) {
            current = null;
            return;
        }
        try {
            current = TARGETS.getOrCreate(key, () -> new TargetSet(width, height, levels));
            FAILED_TARGET_RETRY.succeeded(key);
        } catch (RuntimeException failure) {
            current = null;
            FAILED_TARGET_RETRY.failed(key, frame, ALLOCATION_RETRY_FRAMES);
            com.lowdragmc.photon.Photon.LOGGER.warn(
                    "Could not allocate {}x{} bloom targets; skipping builtin bloom for {} frames",
                    width, height, ALLOCATION_RETRY_FRAMES, failure);
        }
    }

    public static RenderTarget postTarget(RenderTarget srcTarget) {
        int levels = PhotonConfig.INSTANCE.bloomMipLevel.get();
        if (current == null || current.width != srcTarget.width || current.height != srcTarget.height
                || current.requestedLevels != levels) {
            prepareTarget(srcTarget.width, srcTarget.height);
        }
        var targets = current;
        if (targets == null || targets.mips.isEmpty() || targets.output == null) return srcTarget;
        renderBloom(srcTarget, targets);
        return targets.output;
    }

    /** Frame boundary: release cached target sets that have not served a view for 120 frames. */
    public static void onFrameEnd() {
        frame++;
        FAILED_TARGET_RETRY.advanceTo(frame);
        var expired = TARGETS.endFrame();
        if (current != null && expired.contains(current)) current = null;
        try {
            ResourceDisposal.disposeAll(expired, TargetSet::destroy);
        } catch (RuntimeException | Error failure) {
            com.lowdragmc.photon.Photon.LOGGER.error(
                    "Could not dispose one or more expired bloom target sets", failure);
        }
    }

    private static HDRTarget resize(@Nullable HDRTarget target, int width, int height) {
        return RenderPassPipeline.resize(target, width, height, false);
    }

    private static void destroyAfterFailedBuild(@Nullable HDRTarget target, Throwable failure) {
        if (target == null) return;
        try {
            target.destroyBuffers();
        } catch (RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
        }
    }

    private static void renderBloom(RenderTarget srcTarget, TargetSet targets) {
        var mips = targets.mips;
        boolean debugGroup = Platform.isDevEnv() && GL.getCapabilities().GL_KHR_debug;
        if (debugGroup) {
            GL46.glPushDebugGroup(GL46.GL_DEBUG_SOURCE_APPLICATION, 0, "photon_bloom");
        }
        try {
            var brightPassShader = PhotonShaders.getBrightPassShader();
            var finalCombinePassShader = PhotonShaders.getBloomFinalScatterPassShader();

            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();

            brightPassShader.setSampler("inputSampler", srcTarget);
            brightPassShader.safeGetUniform("Threshold").set(PhotonConfig.INSTANCE.bloomThreshold.get().floatValue());
            blitShader(brightPassShader, mips.get(0), false);

            // down-sampling
            var downSampling = PhotonShaders.getDownSamplingShader();
            for (int i = 1; i < mips.size(); i++) {
                var input = mips.get(i - 1);
                downSampling.setSampler("inputSampler", input);
                downSampling.safeGetUniform("inputResolution").set((float) input.width, (float) input.height);
                blitShader(downSampling, mips.get(i), false);
            }

            // up-sampling: add each lower-resolution contribution into the existing higher-resolution mip.
            var upSampling = PhotonShaders.getUpSamplingShader();
            upSampling.safeGetUniform("filterRadius").set(0.005f);
            for (int i = mips.size() - 2; i >= 0; i--) {
                upSampling.setSampler("inputSampler", mips.get(i + 1));
                blitShaderAdditive(upSampling, mips.get(i));
            }

            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            finalCombinePassShader.setSampler("inputA", mips.get(0));
            finalCombinePassShader.setSampler("inputB", srcTarget);
            finalCombinePassShader.safeGetUniform("BloomIntensive").set(PhotonConfig.INSTANCE.bloomIntensity.get().floatValue());
            blitShader(finalCombinePassShader, targets.output, false);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            if (debugGroup) GL46.glPopDebugGroup();
        }
    }

    public static void blitShader(ShaderInstance shaderInstance, RenderTarget dist, boolean doClear) {
        if (doClear) {
            dist.clear(Minecraft.ON_OSX);
            dist.bindWrite(false);
        } else {
            dist.bindWrite(true);
        }
        shaderInstance.apply();
        try {
            SceneBlit.drawFullscreenQuad();
        } finally {
            shaderInstance.clear();
        }
    }

    private static void blitShaderAdditive(ShaderInstance shaderInstance, RenderTarget dist) {
        dist.bindWrite(true);
        // ShaderInstance.apply() applies the JSON blend state, so additive composition must be
        // configured after it and immediately before the draw.
        shaderInstance.apply();
        try {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            RenderSystem.blendEquation(GL30.GL_FUNC_ADD);
            SceneBlit.drawFullscreenQuad();
        } finally {
            shaderInstance.clear();
        }
    }
}
