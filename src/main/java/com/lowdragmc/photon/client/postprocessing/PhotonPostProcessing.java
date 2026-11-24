package com.lowdragmc.photon.client.postprocessing;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.client.shader.HDRTarget;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.UISurface;
import com.lowdragmc.photon.PhotonConfig;
import com.lowdragmc.photon.client.PhotonShaders;
import com.lowdragmc.photon.client.fx.FXBloom;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;
import com.lowdragmc.photon.client.postfx.runtime.SceneBlit;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import lombok.Getter;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL46;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class PhotonPostProcessing {
    public static class Mip {
        @Getter
        private HDRTarget swapA;
//        @Getter
//        private HDRTarget swapB;

        public void updateScreenSize(int width, int height) {
            swapA = resize(swapA, width, height);
//            swapB = resize(swapB, width, height);
        }

        public void clear() {
            if (swapA != null) {
                swapA.destroyBuffers();
            }
//            if (swapB != null) {
//                swapB.destroyBuffers();
//            }
        }
    }
    private static int LAST_WIDTH, LAST_HEIGHT, LAST_MIP_LEVEL;
    private static boolean bloomFailureReported;
    private static HDRTarget HIGH_LIGHT, OUTPUT;
    private static final List<Mip> MIPS = new ArrayList<>();

    /**
     * How many pyramid levels a {@code width}×{@code height} frame has room for, {@code configured} at most.
     *
     * <p>The bright pass is half the frame and every level halves again, and a level needs at least one texel on
     * both axes: {@link HDRTarget} (like vanilla's {@code RenderTarget}) rejects a 0×0 allocation with an
     * {@code IllegalArgumentException}, which here would come out of the level render as a crash. The config allows
     * up to 10 levels, but a 1080p frame only holds 9 (the tenth is 1080 / 2048 = 0 rows), a 720p frame 8, and the
     * editor's scene preview or a minimised window can be far smaller than that. 0 means no level fits, so bloom is
     * skipped for that frame size.
     */
    public static int fittingMipLevels(int configured, int width, int height) {
        int smallest = Math.min(width, height);
        if (configured < 1 || smallest < 4) return 0;
        int halvings = 31 - Integer.numberOfLeadingZeros(smallest); // floor(log2): how often it halves before 0
        return Math.min(configured, halvings - 1);
    }

    /** The number of pyramid levels currently allocated; 0 while the frame is too small for bloom. */
    public static int mipLevels() {
        return MIPS.size();
    }

    public static void prepareTarget(int width, int height) {
        int configured = PhotonConfig.INSTANCE.bloomMipLevel.get();
        if (LAST_WIDTH == width && LAST_HEIGHT == height && LAST_MIP_LEVEL == configured) return;
        int mipLevel = fittingMipLevels(configured, width, height);

        MIPS.forEach(Mip::clear);
        MIPS.clear();
        if (mipLevel == 0) {
            // Too small for even one level: free the chain so postTarget() hands the frame back untouched.
            HIGH_LIGHT = release(HIGH_LIGHT);
            OUTPUT = release(OUTPUT);
        } else {
            HIGH_LIGHT = resize(HIGH_LIGHT, width / 2, height / 2);
            OUTPUT = resize(OUTPUT, width, height);
            for (int i = 0; i < mipLevel; i++) {
                MIPS.add(new Mip());
            }

            var w = width / 2;
            var h = height / 2;
            for (Mip mip : MIPS) {
                w = w / 2;
                h = h / 2;
                mip.updateScreenSize(w, h);
            }
        }

        LAST_WIDTH = width;
        LAST_HEIGHT = height;
        LAST_MIP_LEVEL = configured;
    }

    @Nullable
    private static HDRTarget release(@Nullable HDRTarget target) {
        if (target != null) target.destroyBuffers();
        return null;
    }

    public static RenderTarget postTarget(RenderTarget srcTarget) {
        // Nothing prepared (no build ran this frame): hand the frame back untouched rather than
        // indexing an empty mip chain.
        if (MIPS.isEmpty() || HIGH_LIGHT == null || OUTPUT == null
                || PhotonShaders.getBrightPassShader() == null || PhotonShaders.getDownSamplingShader() == null
                || PhotonShaders.getUpSamplingShader() == null || PhotonShaders.getBloomFinalScatterPassShader() == null) {
            bloomFailureReported = false;
            return srcTarget;
        }
        // Intensity 0 = every FX in this build turned bloom off (or none contributed).
        if (FXBloom.intensity() <= 1e-6f) {
            bloomFailureReported = false;
            return srcTarget;
        }
        com.lowdragmc.photon.client.compat.gpu.GpuCompat.begin("fx.bloom");
        try {
            if (!renderBloom(srcTarget)) {
                bloomFailureReported = false;
                return srcTarget;
            }
            bloomFailureReported = false;
            return OUTPUT == null ? srcTarget : OUTPUT;
        } catch (RuntimeException failure) {
            if (!bloomFailureReported) {
                bloomFailureReported = true;
                com.lowdragmc.photon.Photon.LOGGER.error("Photon bloom pass failed; keeping the unprocessed FX frame", failure);
            }
            // The caller still has to write this frame back. Undo the partial pass state so a shader
            // failure cannot leave depth/color writes disabled or the wrong framebuffer bound.
            srcTarget.bindWrite(true);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL30.GL_FUNC_ADD);
            RenderSystem.defaultBlendFunc();
            return srcTarget;
        } finally {
            com.lowdragmc.photon.client.compat.gpu.GpuCompat.end("fx.bloom");
        }
    }

    private static HDRTarget resize(@Nullable HDRTarget target, int width, int height) {
        return RenderPassPipeline.resize(target, width, height, false);
    }

    private static boolean renderBloom(RenderTarget srcTarget) {
        boolean debugGroup = Platform.isDevEnv() && GL.getCapabilities().GL_KHR_debug;
        if (debugGroup) GL46.glPushDebugGroup(GL46.GL_DEBUG_SOURCE_APPLICATION, 0, "photon_bloom");
        try {
            var brightPassShader = PhotonShaders.getBrightPassShader();
            // A missing program means the pass has nothing to read OR write: getUpSamplingShader()'s
            // targets are derived from this chain, so continuing would bind null samplers and leave the
            // caller with a black or shredded frame. Skip the whole effect instead (photon:slash and any
            // other uninstalled shader still let the FX draw through the fallback material).
            if (brightPassShader == null || PhotonShaders.getDownSamplingShader() == null
                    || PhotonShaders.getUpSamplingShader() == null
                    || PhotonShaders.getBloomFinalScatterPassShader() == null) {
                return false;
            }
    //        var separableBlur = PhotonShaders.getSeparableBlurShader();
    //        var combinePassShader = PhotonConfig.INSTANCE.bloomMode.get() == PhotonConfig.BloomMode.ADD ?
    //                PhotonShaders.getBloomAddPassShader() : PhotonShaders.getBloomScatterPassShader();
            var finalCombinePassShader = PhotonShaders.getBloomFinalScatterPassShader();

            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();

            brightPassShader.setSampler("inputSampler", srcTarget);
            brightPassShader.safeGetUniform("Threshold").set(PhotonConfig.INSTANCE.bloomThreshold.get().floatValue());
            // The shader's soft-knee uniform defaults to zero in GLSL. Without uploading it, the
            // threshold is a hard cut and most normally lit FX never enter the bloom chain at all.
            brightPassShader.safeGetUniform("Knee").set(0.5f);
            // Scene mask: bloom only what this build ADDED to the frame, not the frame itself.
            //
            // On the in-place path DRAW_TARGET is a working copy of the frame plus the FX drawn on top
            // of it, and the frame Photon was handed is still untouched at this point (the write-back
            // happens after this chain). Comparing the two therefore isolates the FX. That matters
            // because at the shipped threshold the sky, the sun, clouds and lit terrain cross the cut on
            // their own: adding their blurred copy over the whole screen is what made bloom read as a
            // distortion instead of a glow around the effects.
            //
            // The other paths hand us a standalone FX layer (transparent accumulator) or the frame
            // itself, where there is nothing to compare against, so the mask stays neutral there.
            var sceneTarget = UISurface.currentTarget();
            int sceneTexture = sceneTarget.getColorTextureId();
            int srcTexture = srcTarget.getColorTextureId();
            boolean maskable = RenderPassPipeline.isSceneBackedBuild()
                    && sceneTexture != 0 && sceneTexture != srcTexture
                    && sceneTarget.width == srcTarget.width && sceneTarget.height == srcTarget.height;
            brightPassShader.setSampler("sceneSampler", maskable ? sceneTexture : srcTexture);
            brightPassShader.safeGetUniform("SceneMask").set(maskable ? 1f : 0f);
            blitShader(brightPassShader, HIGH_LIGHT, false);

            // down-sampling
            var downSampling = PhotonShaders.getDownSamplingShader();
            RenderTarget input = HIGH_LIGHT;
            for (Mip mip : MIPS) {
                var swapA = mip.swapA;
    //            var swapB = mip.swapB;
                downSampling.setSampler("inputSampler", input);
                downSampling.safeGetUniform("inputResolution").set((float) input.width, (float) input.height);
                blitShader(downSampling, swapA, false);
    //
    //            separableBlur.setSampler("inputSampler", swapA);
    //            separableBlur.safeGetUniform("BlurDir").set(1f, 0f);
    //            separableBlur.safeGetUniform("OutSize").set((float) swapA.width, (float) swapA.height);
    //            blitShader(separableBlur, swapB);
    //
    //            separableBlur.setSampler("inputSampler", swapB);
    //            separableBlur.safeGetUniform("BlurDir").set(0f, 1f);
    //            separableBlur.safeGetUniform("OutSize").set((float) swapB.width, (float) swapB.height);
    //            blitShader(separableBlur, swapA);

                input = swapA;
            }


            // up-sampling
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            RenderSystem.blendEquation(GL30.GL_FUNC_ADD);

            var upSampling = PhotonShaders.getUpSamplingShader();
            var lowRes = MIPS.get(MIPS.size() - 1).swapA;
            upSampling.safeGetUniform("filterRadius").set(0.005f);
            for (int i = MIPS.size() - 2; i >= 0; i--) {
                var mip = MIPS.get(i).swapA;
                upSampling.setSampler("inputSampler", lowRes);
                blitShader(upSampling, mip, false);
                lowRes = mip;
            }

    //        // down-sampling
    //        RenderTarget input = HIGH_LIGHT;
    //        for (Mip mip : MIPS) {
    //            var swapA = mip.swapA;
    //            var swapB = mip.swapB;
    //            separableBlur.setSampler("inputSampler", input);
    //            separableBlur.safeGetUniform("BlurDir").set(1f, 0f);
    //            separableBlur.safeGetUniform("OutSize").set((float) swapA.width, (float) swapA.height);
    //            blitShader(separableBlur, swapA);
    //
    //            separableBlur.setSampler("inputSampler", swapA);
    //            separableBlur.safeGetUniform("BlurDir").set(0f, 1f);
    //            separableBlur.safeGetUniform("OutSize").set((float) swapB.width, (float) swapB.height);
    //            blitShader(separableBlur, swapB);
    //            input = swapB;
    //        }
    //
    //        // up-sampling
    //        RenderTarget lowRes = MIPS.getLast().swapB;
    //        var bloomIntensity = PhotonConfig.INSTANCE.bloomIntensity.get().floatValue();
    //        combinePassShader.safeGetUniform("BloomIntensive").set(1f);
    //        combinePassShader.safeGetUniform("BloomScatter").set(0.7f);
    //        for (int i = MIPS.size() - 2; i >= 0; i--) {
    //            var highRes = MIPS.get(i);
    //            combinePassShader.setSampler("inputA", lowRes);
    //            combinePassShader.setSampler("inputB", highRes.getSwapB());
    //            blitShader(combinePassShader, highRes.getSwapA());
    //            lowRes = highRes.getSwapA();
    //        }

            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            finalCombinePassShader.setSampler("inputA", MIPS.get(0).swapA);
            finalCombinePassShader.setSampler("inputB", srcTarget);
            finalCombinePassShader.safeGetUniform("BloomIntensive").set(FXBloom.intensity());
            blitShader(finalCombinePassShader, OUTPUT, false);

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();


            return true;
        } finally {
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
        try {
            shaderInstance.apply();
            SceneBlit.drawFullscreenQuad();
        } finally {
            shaderInstance.clear();
        }
    }
}
