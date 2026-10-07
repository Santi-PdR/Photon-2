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
    private static HDRTarget OUTPUT;
    private static final List<Mip> MIPS = new ArrayList<>();

    public static void prepareTarget(int width, int height) {
        int mipLevel = PhotonConfig.INSTANCE.bloomMipLevel.get();
        var mipSizes = BloomMipLayout.compute(width, height, mipLevel);
        if (LAST_WIDTH == width && LAST_HEIGHT == height && MIPS.size() == mipSizes.size()) return;

        OUTPUT = resize(OUTPUT, width, height);

        MIPS.forEach(Mip::clear);
        MIPS.clear();
        for (var size : mipSizes) {
            var mip = new Mip();
            mip.updateScreenSize(size.width(), size.height());
            MIPS.add(mip);
        }

        LAST_WIDTH = width;
        LAST_HEIGHT = height;
    }

    public static RenderTarget postTarget(RenderTarget srcTarget) {
        if (MIPS.isEmpty()) return srcTarget;
        renderBloom(srcTarget);
        return OUTPUT;
    }

    private static HDRTarget resize(@Nullable HDRTarget target, int width, int height) {
        return RenderPassPipeline.resize(target, width, height, false);
    }

    private static void renderBloom(RenderTarget srcTarget) {
        if (Platform.isDevEnv() && GL.getCapabilities().GL_KHR_debug) {
            GL46.glPushDebugGroup(GL46.GL_DEBUG_SOURCE_APPLICATION, 0, "photon_bloom");
        }

        var brightPassShader = PhotonShaders.getBrightPassShader();
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
        blitShader(brightPassShader, MIPS.get(0).swapA, false);

        // down-sampling
        var downSampling = PhotonShaders.getDownSamplingShader();
        for (int i = 1; i < MIPS.size(); i++) {
            var input = MIPS.get(i - 1).swapA;
            downSampling.setSampler("inputSampler", input);
            downSampling.safeGetUniform("inputResolution").set((float) input.width, (float) input.height);
            blitShader(downSampling, MIPS.get(i).swapA, false);
        }


        // up-sampling: add each lower-resolution contribution into the existing higher-resolution mip.
        var upSampling = PhotonShaders.getUpSamplingShader();
        upSampling.safeGetUniform("filterRadius").set(0.005f);
        for (int i = MIPS.size() - 2; i >= 0; i--) {
            upSampling.setSampler("inputSampler", MIPS.get(i + 1).swapA);
            blitShaderAdditive(upSampling, MIPS.get(i).swapA);
        }

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

    private static void blitShaderAdditive(ShaderInstance shaderInstance, RenderTarget dist) {
        dist.bindWrite(true);
        // ShaderInstance.apply() applies the JSON blend state, so additive composition must be
        // configured after it and immediately before the draw.
        shaderInstance.apply();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.blendEquation(GL30.GL_FUNC_ADD);
        SceneBlit.drawFullscreenQuad();
        shaderInstance.clear();
    }
}
