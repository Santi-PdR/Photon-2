package com.lowdragmc.photon.client.compat.gpu;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.FXCompositeMode;
import com.lowdragmc.photon.client.light.ShadowMode;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Pins the AMD/Intel defence of round 25, on a machine that is not the one it was written for.
 *
 * <p>The driver detection must put the Vega 11's strings on the conservative profile and leave NVIDIA and Mesa (the CI
 * runner's llvmpipe among them) on the full one; the profile must turn exactly the four paths off and nothing else;
 * {@link GlUploads} must hand back the texture state it found, on the unit it found it, which is the whole point of
 * it; {@link GpuCompat#end} must catch a GL error raised inside a pass; and the freeze report must be writable and
 * carry the driver, the last pass and the config. Runs inside the client smoke test, on the render thread.
 */
@OnlyIn(Dist.CLIENT)
public final class GpuCompatRegression {
    private GpuCompatRegression() {
    }

    public static void verify() throws Exception {
        RenderSystem.assertOnRenderThread();
        check(GpuCompat.isInitialised(), "the GPU profile was never decided: no render tick reached GpuCompat.init()");

        // the detection
        check(GpuCompat.isSuspectDriver("ATI Technologies Inc.", "AMD Radeon(TM) Vega 11 Graphics", "4.6.14800 Compatibility Profile Context 23.20.15.01"),
                "the Vega 11 Windows driver was not recognised as suspect");
        check(GpuCompat.isSuspectDriver("Intel", "Intel(R) UHD Graphics 630", "4.6.0 - Build 31.0.101.2111"),
                "the Intel Windows driver was not recognised as suspect");
        check(!GpuCompat.isSuspectDriver("NVIDIA Corporation", "NVIDIA GeForce GTX 1050/PCIe/SSE2", "4.6.0 NVIDIA 582.78"),
                "the NVIDIA driver was put on the conservative profile");
        check(!GpuCompat.isSuspectDriver("AMD", "AMD Radeon RX 6600 (radeonsi, navi23, LLVM 15.0.7, DRM 3.54, 6.5.0)", "4.6 (Core Profile) Mesa 23.2.1"),
                "Mesa on an AMD card was put on the conservative profile");
        check(!GpuCompat.isSuspectDriver("Mesa", "llvmpipe (LLVM 15.0.7, 256 bits)", "4.5 (Core Profile) Mesa 23.2.1-1ubuntu3"),
                "llvmpipe was put on the conservative profile");
        check(!GpuCompat.isSuspectDriver(null, null, null), "an unknown driver was put on the conservative profile");

        // the profile's reach
        check(GpuCompat.shadowModeFor(ShadowMode.VOXEL, true) == ShadowMode.SCREEN, "the conservative profile kept voxel shadows");
        check(GpuCompat.shadowModeFor(ShadowMode.SCREEN, true) == ShadowMode.SCREEN, "the conservative profile changed screen-space shadows");
        check(GpuCompat.shadowModeFor(ShadowMode.OFF, true) == ShadowMode.OFF, "the conservative profile turned shadows on");
        check(GpuCompat.shadowModeFor(ShadowMode.VOXEL, false) == ShadowMode.VOXEL, "the full profile changed voxel shadows");
        check(GpuCompat.compositeModeFor(FXCompositeMode.LATE, true) == FXCompositeMode.VANILLA, "the conservative profile kept the late layer");
        check(GpuCompat.compositeModeFor(FXCompositeMode.VANILLA, true) == FXCompositeMode.VANILLA, "the conservative profile changed in-place compositing");
        check(GpuCompat.compositeModeFor(FXCompositeMode.LATE, false) == FXCompositeMode.LATE, "the full profile changed the late layer");

        // uploads leave the texture state as they found it
        int scratch = TextureUtil.generateTextureId();
        int probe = TextureUtil.generateTextureId();
        try {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            int unit0Before = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 3);
            GlStateManager._bindTexture(probe);
            long token = GlUploads.bind(scratch);
            check(GlStateManager._getActiveTexture() == GL13.GL_TEXTURE0, "GlUploads.bind did not move to unit 0");
            check(GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D) == scratch, "GlUploads.bind did not bind the upload texture");
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GlUploads.restore(token);
            check(GlStateManager._getActiveTexture() == GL13.GL_TEXTURE0 + 3, "GlUploads.restore did not return to the active unit it found");
            check(GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D) == probe, "GlUploads.restore disturbed the active unit's texture");
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            check(GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D) == unit0Before,
                    "GlUploads.restore did not put unit 0's texture back (" + GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D) + " instead of " + unit0Before + ")");
        } finally {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            TextureUtil.releaseTextureId(scratch);
            TextureUtil.releaseTextureId(probe);
        }

        // a GL error inside a pass is counted against that pass
        GpuCompat.begin("regression");
        GL11.glEnable(0x0BAD); // not a capability: GL_INVALID_ENUM on every driver
        GpuCompat.end("regression");
        check(GpuCompat.errorCount("regression") >= 1, "a GL error raised inside a Photon pass was not counted");
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "GpuCompat.end left the GL error flag set");

        // the freeze report
        var report = RenderWatchdog.selfTest();
        var text = Files.readString(report, StandardCharsets.UTF_8);
        check(text.contains("GL renderer: " + GpuCompat.renderer()), "the freeze report does not name the renderer");
        check(text.contains("Last Photon GPU stage: regression (done)"), "the freeze report does not name the last pass");
        check(text.contains("Config: fx_composite_mode=") && text.contains("shadow_mode="), "the freeze report does not carry the config");
        check(text.contains("Render thread: ") && text.contains("    at "), "the freeze report has no render thread stack");
        check(text.contains("GL errors inside Photon's pass \"regression\": "), "the freeze report does not list the pass error counts");
        Files.deleteIfExists(report);
        check(RenderWatchdog.isArmed(), "the freeze watchdog is not armed");

        Photon.LOGGER.info("[gpu-compat-regression-smoke] AMD/Intel Windows drivers get the conservative profile, uploads restore the texture state, pass errors are counted and the freeze report is complete");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
