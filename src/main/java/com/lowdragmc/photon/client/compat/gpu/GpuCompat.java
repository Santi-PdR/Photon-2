package com.lowdragmc.photon.client.compat.gpu;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonConfig;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.FXCompositeMode;
import com.lowdragmc.photon.client.light.ShadowMode;
import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Driver-aware switches for Photon's GPU work, plus the cheap tracing the freeze reports are built from.
 *
 * <p>Photon's frame uses more of OpenGL than vanilla does: integer textures read with {@code texelFetch}, float
 * render targets whose colour format is re-specified after creation, depth copied between framebuffers by blit,
 * foreign depth attachments, instanced draws fed from texture buffers. Every one of those is legal GL 3.3 and runs
 * clean on NVIDIA; the first AMD integrated GPU a user played on (a Vega 11, Windows driver) corrupted the terrain
 * textures and locked the client in {@code glfwSwapBuffers} the moment any effect appeared — the signature of the GPU
 * faulting and the driver never handing the context back. Without that machine to bisect on, the defence is in two
 * layers:
 *
 * <ol>
 *   <li><b>A conservative profile</b> ({@link #safeMode()}), chosen automatically on the driver families known to be
 *       fragile and selectable by hand: screen-space shadows instead of the voxel world (no integer textures, no
 *       visibility atlas), no volumetric pass, the particle pipeline composited in place ({@link FXCompositeMode#VANILLA},
 *       no opaque-depth snapshot or foreign depth attachment) and every emitter drawn through the CPU path (no
 *       instancing, no texture buffers). What is left is the same vanilla-grade GL the rest of Minecraft runs on.</li>
 *   <li><b>Tracing</b> that costs nothing when nothing is wrong: each Photon pass is bracketed by
 *       {@link #begin}/{@link #end}, which record the pass name for {@link RenderWatchdog} and read back
 *       {@code glGetError} once per pass — the errors a stricter driver raises where NVIDIA stays silent are exactly
 *       the breadcrumbs needed to find a fault from a user's log. With {@code gpu_sync} (or
 *       {@code -Dphoton.gpuSync=true}) each pass also ends on {@code glFinish}, so a GPU hang surfaces inside the pass
 *       that caused it instead of at the next buffer swap.</li>
 * </ol>
 *
 * <p>Render thread unless noted; the few fields the watchdog thread reads are volatile.
 */
@OnlyIn(Dist.CLIENT)
public final class GpuCompat {
    /** GL errors logged per pass before the log falls silent about it (the counters keep counting). */
    private static final int ERROR_LOGS_PER_STAGE = 4;
    private static final int RECENT_PLAYBACKS = 12;
    private static final boolean SYNC_PROPERTY = Boolean.getBoolean("photon.gpuSync");

    private static volatile boolean initialised;
    private static int initAttempts;
    private static volatile boolean safe;
    private static volatile GpuProfile profile = GpuProfile.AUTO;
    private static volatile String vendor = "unknown";
    private static volatile String renderer = "unknown";
    private static volatile String version = "unknown";
    private static volatile String reason = "not initialised";
    private static volatile String lastStage = "none";
    private static volatile long lastStageNanos = System.nanoTime();
    private static volatile long stageCount;
    private static volatile long foreignErrors;
    private static final Map<String, long[]> ERRORS = new ConcurrentHashMap<>();
    private static final ArrayDeque<String> RECENT = new ArrayDeque<>();

    private GpuCompat() {
    }

    /** Reads the driver strings and decides the profile; render thread, once (later calls are free). */
    public static void init() {
        if (initialised || !RenderSystem.isOnRenderThread()) return;
        try {
            vendor = String.valueOf(GlUtil.getVendor());
            renderer = String.valueOf(GlUtil.getRenderer());
            version = String.valueOf(GlUtil.getOpenGLVersion());
        } catch (Throwable t) {
            // no GL context on this thread yet, or a driver that cannot answer: try again on a later frame, a few times
            if (++initAttempts < 3) return;
            Photon.LOGGER.warn("[gpu-compat] could not read the OpenGL driver strings", t);
        }
        decide(configuredProfile());
        initialised = true;
        Photon.LOGGER.info("[gpu-compat-smoke] vendor=\"{}\" renderer=\"{}\" version=\"{}\" profile={} safeMode={} ({}) gpuSync={}",
                vendor, renderer, version, profile, safe, reason, gpuSync());
        if (safe) {
            Photon.LOGGER.warn("[gpu-compat] Photon is using its conservative GPU pipeline on this driver: screen-space"
                    + " shadows, no volumetric lighting, FX composited in place, no GPU instancing. Set gpu_profile = \"FULL\""
                    + " under [compat] in config/photon-client.toml to use everything; if the client then freezes, send"
                    + " photon/diagnostics/freeze-*.txt and the log.");
        }
    }

    public static boolean isInitialised() {
        return initialised;
    }

    /**
     * Whether the conservative pipeline is in force. Follows the config live, so flipping the profile in the config
     * screen takes effect on the next frame; before the driver is known (or off the render thread) it is the full
     * pipeline, which is what every frame before the first render tick got anyway.
     */
    public static boolean safeMode() {
        if (!initialised) {
            init();
            if (!initialised) return false;
        }
        var configured = configuredProfile();
        if (configured != profile) {
            decide(configured);
            Photon.LOGGER.info("[gpu-compat] profile changed to {}: safeMode={} ({})", profile, safe, reason);
        }
        return safe;
    }

    /** The voxel shadow world and its visibility maps: integer textures, the one path with no vanilla equivalent. */
    public static ShadowMode shadowMode(ShadowMode configured) {
        return shadowModeFor(configured, safeMode());
    }

    /** Halos, beams and fog volumes. */
    public static boolean volumetrics() {
        return !safeMode();
    }

    /** Instanced particle draws fed from texture buffers; off, every emitter takes the CPU path with the same picture. */
    public static boolean gpuInstancing() {
        return !safeMode();
    }

    /** The global FX composite mode after the profile: the late layer needs the opaque-depth snapshot and a foreign
     *  depth attachment, so the conservative pipeline composites in place. */
    public static FXCompositeMode compositeMode(FXCompositeMode configured) {
        return compositeModeFor(configured, safeMode());
    }

    // the decisions as pure functions, for the regression

    static ShadowMode shadowModeFor(ShadowMode configured, boolean safe) {
        return safe && configured == ShadowMode.VOXEL ? ShadowMode.SCREEN : configured;
    }

    static FXCompositeMode compositeModeFor(FXCompositeMode configured, boolean safe) {
        return safe && configured == FXCompositeMode.LATE ? FXCompositeMode.VANILLA : configured;
    }

    /**
     * The driver families the conservative profile is for: AMD's and Intel's own Windows OpenGL drivers. Mesa
     * (Linux, and llvmpipe on the CI runner) is a different code base with a different record and keeps the full
     * pipeline, as does everything that is not recognised — an unknown driver is not evidence.
     */
    static boolean isSuspectDriver(@Nullable String vendor, @Nullable String renderer, @Nullable String version) {
        var v = vendor == null ? "" : vendor.toLowerCase(Locale.ROOT);
        var r = renderer == null ? "" : renderer.toLowerCase(Locale.ROOT);
        var g = version == null ? "" : version.toLowerCase(Locale.ROOT);
        if (g.contains("mesa") || r.contains("mesa") || r.contains("llvmpipe") || r.contains("radeonsi") || r.contains("zink")) {
            return false;
        }
        boolean amd = v.contains("ati technologies") || v.contains("advanced micro devices") || v.equals("amd")
                || v.startsWith("amd ") || r.contains("radeon") || r.startsWith("amd ") || r.contains(" amd ");
        boolean intel = v.contains("intel") || r.contains("intel(r)") || r.startsWith("intel ");
        return amd || intel;
    }

    private static GpuProfile configuredProfile() {
        try {
            return PhotonConfig.INSTANCE.gpuProfile.get();
        } catch (RuntimeException notLoaded) {
            return GpuProfile.AUTO;
        }
    }

    private static void decide(GpuProfile configured) {
        profile = configured;
        switch (configured) {
            case FULL -> {
                safe = false;
                reason = "gpu_profile=FULL";
            }
            case SAFE -> {
                safe = true;
                reason = "gpu_profile=SAFE";
            }
            default -> {
                boolean suspect = isSuspectDriver(vendor, renderer, version);
                safe = suspect;
                reason = suspect ? "gpu_profile=AUTO: AMD/Intel Windows driver" : "gpu_profile=AUTO: driver not on the list";
            }
        }
    }

    // ---- tracing ----

    /** Whether every Photon pass ends on {@code glFinish}: a hang then stops inside the pass that caused it. */
    public static boolean gpuSync() {
        if (SYNC_PROPERTY) return true;
        try {
            return PhotonConfig.INSTANCE.gpuSync.get();
        } catch (RuntimeException notLoaded) {
            return false;
        }
    }

    /** A Photon pass starts: errors other code left behind are swallowed here, so {@link #end} counts only ours. */
    public static void begin(String stage) {
        lastStage = stage;
        lastStageNanos = System.nanoTime();
        for (int i = 0; i < 8 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) foreignErrors++;
    }

    /** A Photon pass ends: optionally waits for the GPU, then reads back and counts the pass's GL errors. */
    public static void end(String stage) {
        if (gpuSync()) GL11.glFinish();
        int first = GL11.GL_NO_ERROR;
        int count = 0;
        for (int i = 0; i < 8; i++) {
            int error = GL11.glGetError();
            if (error == GL11.GL_NO_ERROR) break;
            if (first == GL11.GL_NO_ERROR) first = error;
            count++;
        }
        if (count > 0) {
            var counters = ERRORS.computeIfAbsent(stage, k -> new long[2]);
            counters[0] += count;
            if (counters[1] < ERROR_LOGS_PER_STAGE) {
                counters[1]++;
                Photon.LOGGER.warn("[gpu-error] stage={} glGetError=0x{} ({}) x{} on \"{}\"{}", stage,
                        Integer.toHexString(first).toUpperCase(Locale.ROOT), errorName(first), count, renderer,
                        counters[1] == ERROR_LOGS_PER_STAGE ? "; further errors of this pass are counted, not logged" : "");
            }
        }
        stageCount++;
        lastStage = stage + " (done)";
        lastStageNanos = System.nanoTime();
    }

    /** Total errors {@link #end} counted for a pass; for the regression and the freeze report. */
    public static long errorCount(String stage) {
        var counters = ERRORS.get(stage);
        return counters == null ? 0 : counters[0];
    }

    /** An effect started playing; the last few are listed in a freeze report. Any thread. */
    public static void notePlayback(String label) {
        synchronized (RECENT) {
            if (RECENT.size() == RECENT_PLAYBACKS) RECENT.pollFirst();
            RECENT.addLast(label + " @" + (System.currentTimeMillis() / 1000L % 100000L));
        }
    }

    /** What a freeze report says about the GPU side; any thread. */
    public static List<String> describe() {
        var lines = new ArrayList<String>();
        lines.add("GL vendor: " + vendor);
        lines.add("GL renderer: " + renderer);
        lines.add("GL version: " + version);
        lines.add("Photon GPU profile: " + profile + " -> safeMode=" + safe + " (" + reason + "), initialised=" + initialised
                + ", gpuSync=" + gpuSync());
        lines.add("Last Photon GPU stage: " + lastStageDescription() + ", stages so far: " + stageCount);
        lines.add("GL errors raised by other code before Photon's passes: " + foreignErrors);
        if (ERRORS.isEmpty()) {
            lines.add("GL errors inside Photon's passes: none");
        } else {
            for (var entry : ERRORS.entrySet()) {
                lines.add("GL errors inside Photon's pass \"" + entry.getKey() + "\": " + entry.getValue()[0]);
            }
        }
        synchronized (RECENT) {
            lines.add("Recent FX playbacks (label @unix seconds mod 100000): " + (RECENT.isEmpty() ? "none" : String.join(", ", RECENT)));
        }
        return lines;
    }

    /** The last profile decision, without re-reading the config or touching GL; any thread. */
    public static boolean lastDecisionSafe() {
        return safe;
    }

    /** The last pass bracketed by {@link #begin}/{@link #end} and how long ago; any thread. */
    public static String lastStageDescription() {
        return lastStage + " (" + String.format(Locale.ROOT, "%.1f", (System.nanoTime() - lastStageNanos) / 1e6) + " ms ago)";
    }

    public static String vendor() {
        return vendor;
    }

    public static String renderer() {
        return renderer;
    }

    private static String errorName(int error) {
        return switch (error) {
            case GL11.GL_INVALID_ENUM -> "GL_INVALID_ENUM";
            case GL11.GL_INVALID_VALUE -> "GL_INVALID_VALUE";
            case GL11.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION";
            case GL11.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY";
            case 0x0506 -> "GL_INVALID_FRAMEBUFFER_OPERATION";
            case 0x0507 -> "GL_CONTEXT_LOST";
            default -> "unknown";
        };
    }
}
