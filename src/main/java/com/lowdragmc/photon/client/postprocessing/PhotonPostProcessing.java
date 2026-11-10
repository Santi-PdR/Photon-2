package com.lowdragmc.photon.client.postprocessing;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.client.shader.HDRTarget;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.UISurface;
import com.lowdragmc.photon.PhotonConfig;
import com.lowdragmc.photon.client.PhotonShaders;
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
    private static int LAST_WIDTH, LAST_HEIGHT;
    private static HDRTarget HIGH_LIGHT, OUTPUT;
    private static final List<Mip> MIPS = new ArrayList<>();

    public static void prepareTarget(int width, int height) {
        int mipLevel = PhotonConfig.INSTANCE.bloomMipLevel.get();
        if (LAST_WIDTH == width && LAST_HEIGHT == height && MIPS.size() == mipLevel) return;

        HIGH_LIGHT = resize(HIGH_LIGHT, width / 2, height / 2);
        OUTPUT = resize(OUTPUT, width, height);

        MIPS.forEach(Mip::clear);
        MIPS.clear();
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

        LAST_WIDTH = width;
        LAST_HEIGHT = height;
    }

    public static RenderTarget postTarget(RenderTarget srcTarget) {
        // Nothing prepared (no build ran this frame): hand the frame back untouched rather than
        // indexing an empty mip chain.
        if (MIPS.isEmpty() || HIGH_LIGHT == null || OUTPUT == null
                || PhotonShaders.getBrightPassShader() == null || PhotonShaders.getDownSamplingShader() == null
                || PhotonShaders.getUpSamplingShader() == null || PhotonShaders.getBloomFinalScatterPassShader() == null) return srcTarget;
        renderBloom(srcTarget);
        return OUTPUT == null ? srcTarget : OUTPUT;
    }

    private static HDRTarget resize(@Nullable HDRTarget target, int width, int height) {
        return RenderPassPipeline.resize(target, width, height, false);
    }

    private static void renderBloom(RenderTarget srcTarget) {
        if (Platform.isDevEnv() && GL.getCapabilities().GL_KHR_debug) {
            GL46.glPushDebugGroup(GL46.GL_DEBUG_SOURCE_APPLICATION, 0, "photon_bloom");
        }

        var brightPassShader = PhotonShaders.getBrightPassShader();
        // A missing program means the pass has nothing to read OR write: getUpSamplingShader()'s
        // targets are derived from this chain, so continuing would bind null samplers and leave the
        // caller with a black or shredded frame. Skip the whole effect instead (photon:slash and any
        // other uninstalled shader still let the FX draw through the fallback material).
        if (brightPassShader == null || PhotonShaders.getDownSamplingShader() == null
                || PhotonShaders.getUpSamplingShader() == null
                || PhotonShaders.getBloomFinalScatterPassShader() == null) {
            if (Platform.isDevEnv() && GL.getCapabilities().GL_KHR_debug) {
                GL46.glPopDebugGroup();
            }
            return;
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
        finalCombinePassShader.safeGetUniform("BloomIntensive").set(PhotonConfig.INSTANCE.bloomIntensity.get().floatValue());
        blitShader(finalCombinePassShader, OUTPUT, false);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();


        if (Platform.isDevEnv() && GL.getCapabilities().GL_KHR_debug) {
            GL46.glPopDebugGroup();
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
        SceneBlit.drawFullscreenQuad();
        shaderInstance.clear();
    }
}
