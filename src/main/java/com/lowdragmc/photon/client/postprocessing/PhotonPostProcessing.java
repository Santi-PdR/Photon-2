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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class PhotonPostProcessing {
    private record TargetKey(int width, int height, int requestedLevels) {}

    private static final class TargetSet {
        final int width;
        final int height;
        final int requestedLevels;
        @Nullable final HDRTarget output;
        final ArrayList<HDRTarget> mips;
        long lastUsedFrame;

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
            output = resize(null, width, height);
            mips = new ArrayList<>(sizes.size());
            for (var size : sizes) {
                mips.add(resize(null, size.width(), size.height()));
            }
        }

        void destroy() {
            if (output != null) output.destroyBuffers();
            for (var mip : mips) mip.destroyBuffers();
        }
    }

    private static final Map<TargetKey, TargetSet> TARGETS = new HashMap<>();
    private static long frame;
    @Nullable private static TargetSet current;

    public static void prepareTarget(int width, int height) {
        int levels = PhotonConfig.INSTANCE.bloomMipLevel.get();
        var key = new TargetKey(width, height, levels);
        current = TARGETS.computeIfAbsent(key, ignored -> new TargetSet(width, height, levels));
        current.lastUsedFrame = frame;
    }

    public static RenderTarget postTarget(RenderTarget srcTarget) {
        int levels = PhotonConfig.INSTANCE.bloomMipLevel.get();
        if (current == null || current.width != srcTarget.width || current.height != srcTarget.height
                || current.requestedLevels != levels) {
            prepareTarget(srcTarget.width, srcTarget.height);
        }
        var targets = current;
        if (targets == null || targets.mips.isEmpty() || targets.output == null) return srcTarget;
        targets.lastUsedFrame = frame;
        renderBloom(srcTarget, targets);
        return targets.output;
    }

    /** Frame boundary: release cached target sets that have not served a view for 120 frames. */
    public static void onFrameEnd() {
        frame++;
        Iterator<Map.Entry<TargetKey, TargetSet>> entries = TARGETS.entrySet().iterator();
        while (entries.hasNext()) {
            var targets = entries.next().getValue();
            if (frame - targets.lastUsedFrame <= 120) continue;
            targets.destroy();
            if (current == targets) current = null;
            entries.remove();
        }
    }

    private static HDRTarget resize(@Nullable HDRTarget target, int width, int height) {
        return RenderPassPipeline.resize(target, width, height, false);
    }

    private static void renderBloom(RenderTarget srcTarget, TargetSet targets) {
        var mips = targets.mips;
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
