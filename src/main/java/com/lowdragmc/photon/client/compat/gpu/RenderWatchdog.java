package com.lowdragmc.photon.client.compat.gpu;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonConfig;
import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Writes a report when the render thread stops producing frames while a world is open.
 *
 * <p>A GPU hang leaves no trace a user can send: no exception, no crash report, the log just stops. The render thread
 * sits in {@code glfwSwapBuffers} until the process is killed. The JVM's other threads keep running, so a daemon
 * thread can notice the silence and write down what the render thread is doing, which Photon pass ran last
 * ({@link GpuCompat}), what the driver is and which effects had just started — the whole picture, from the frozen
 * machine itself, with no steps for the user beyond sending the file.
 *
 * <p>Armed by the first {@link #heartbeat()}; a report needs {@value #STALL_SECONDS} s without a heartbeat after one
 * taken inside a level with no loading overlay up, so world loads, resource reloads and the menus do not count. One
 * report per stall; the heartbeat re-arms it. A stall the render thread comes back from — a shader compile burst on a
 * software renderer, a long world save — was a slow frame, not a freeze: the report gets a closing line saying so and
 * the log an INFO, so the file never has to be guessed at. A real GPU hang never gets that line.
 */
@OnlyIn(Dist.CLIENT)
public final class RenderWatchdog {
    public static final int STALL_SECONDS = 30;
    private static final long POLL_MILLIS = 1000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private static volatile long lastHeartbeat = System.nanoTime();
    private static volatile long frames;
    private static volatile boolean inLevelAtHeartbeat;
    private static volatile boolean reported;
    private static volatile Thread renderThread;
    private static volatile boolean started;
    private static volatile boolean armed;
    private static volatile int reports;
    private static volatile Path lastReport;
    /** Set by the heartbeat that ends a reported stall: how long it lasted; the watchdog thread closes the report. */
    private static volatile long resumedAfterNanos;

    private RenderWatchdog() {
    }

    /** Once per frame, on the render thread. */
    public static void heartbeat() {
        long now = System.nanoTime();
        if (reported) {
            resumedAfterNanos = now - lastHeartbeat;
            reported = false;
        }
        lastHeartbeat = now;
        frames++;
        if (!started) start();
        var mc = Minecraft.getInstance();
        inLevelAtHeartbeat = mc.level != null && mc.getOverlay() == null;
    }

    public static boolean isArmed() {
        return armed;
    }

    private static synchronized void start() {
        if (started) return;
        boolean enabled;
        try {
            enabled = PhotonConfig.INSTANCE.freezeWatchdog.get();
        } catch (RuntimeException notLoaded) {
            return; // the config is not readable yet (first frames of the loading overlay); the next heartbeat retries
        }
        started = true;
        renderThread = Thread.currentThread();
        if (!enabled) {
            Photon.LOGGER.info("[freeze-watchdog-smoke] disabled by config (freeze_watchdog = false)");
            return;
        }
        var thread = new Thread(RenderWatchdog::run, "Photon-FreezeWatchdog");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        armed = true;
        Photon.LOGGER.info("[freeze-watchdog-smoke] armed: a render thread silent for {} s inside a level is reported to photon/diagnostics/freeze-*.txt",
                STALL_SECONDS);
    }

    private static void run() {
        while (true) {
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                return;
            }
            long resumed = resumedAfterNanos;
            if (resumed > 0) {
                resumedAfterNanos = 0;
                closeReport(resumed);
            }
            long stalledNanos = System.nanoTime() - lastHeartbeat;
            if (stalledNanos < STALL_SECONDS * 1_000_000_000L || reported || !inLevelAtHeartbeat) continue;
            reported = true;
            try {
                var path = lastReport = report(String.format(Locale.ROOT, "no frame for %.1f s while a level was open", stalledNanos / 1e9));
                Photon.LOGGER.error("[freeze-watchdog] the render thread has produced no frame for {} s; report written to {}. Last Photon GPU stage: {}",
                        String.format(Locale.ROOT, "%.1f", stalledNanos / 1e9), path, GpuCompat.lastStageDescription());
            } catch (Throwable t) {
                Photon.LOGGER.error("[freeze-watchdog] the render thread has produced no frame for {} s and the report could not be written",
                        String.format(Locale.ROOT, "%.1f", stalledNanos / 1e9), t);
            }
        }
    }

    /** The render thread came back: the last report described a slow frame, and says so from now on. */
    private static void closeReport(long stalledNanos) {
        var seconds = String.format(Locale.ROOT, "%.1f", stalledNanos / 1e9);
        var path = lastReport;
        Photon.LOGGER.info("[freeze-watchdog] the render thread resumed after {} s: {} describes a slow frame, not a freeze", seconds, path);
        if (path == null) return;
        try {
            Files.writeString(path, "\nRESOLVED: the render thread resumed after " + seconds + " s. This was a slow frame, not a GPU freeze.\n",
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // the file may have been collected already; the log line carries the same fact
        }
    }

    /** Writes a report now, whatever the render thread is doing; for the smoke test. Returns the file. */
    public static Path selfTest() throws IOException {
        return report("self-test (not a freeze)");
    }

    private static Path report(String why) throws IOException {
        reports++;
        var text = new StringBuilder(4096);
        text.append("Photon freeze report\n====================\n");
        text.append("Reason: ").append(why).append('\n');
        text.append("Written: ").append(LocalDateTime.now()).append('\n');
        text.append("Frames seen by the watchdog: ").append(frames).append(", reports so far: ").append(reports).append('\n');
        text.append("Photon version: ").append(photonVersion()).append('\n');
        text.append("Java: ").append(System.getProperty("java.version")).append(" on ").append(System.getProperty("os.name"))
                .append(' ').append(System.getProperty("os.arch")).append('\n');
        text.append('\n');
        for (var line : GpuCompat.describe()) text.append(line).append('\n');
        text.append('\n');
        appendConfig(text);
        text.append('\n');
        // who else is on the frame: a "mixin of mod X" claim is checked against this, not against memory
        for (var line : com.lowdragmc.photon.client.compat.MixinAudit.lines()) text.append(line).append('\n');
        text.append(com.lowdragmc.photon.client.compat.MixinAudit.modListLine()).append('\n');
        text.append('\n');
        var render = renderThread;
        text.append("Render thread: ").append(render == null ? "unknown" : render.getName() + " state=" + render.getState()).append('\n');
        if (render != null) {
            for (var frame : render.getStackTrace()) text.append("    at ").append(frame).append('\n');
        }
        text.append('\n');
        text.append("Other threads:\n");
        for (var entry : Thread.getAllStackTraces().entrySet()) {
            var thread = entry.getKey();
            if (thread == render) continue;
            text.append("  ").append(thread.getName()).append(" state=").append(thread.getState());
            var stack = entry.getValue();
            if (stack.length > 0) text.append(" top=").append(stack[0]);
            text.append('\n');
        }
        Path directory = Platform.getGamePath().resolve("photon").resolve("diagnostics");
        Files.createDirectories(directory);
        Path file = directory.resolve("freeze-" + STAMP.format(LocalDateTime.now()) + "-" + reports + ".txt");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private static void appendConfig(StringBuilder text) {
        try {
            var config = PhotonConfig.INSTANCE;
            // the last decision: the watchdog thread must not touch GL or re-decide the profile
            boolean safe = GpuCompat.lastDecisionSafe();
            text.append("Config: fx_composite_mode=").append(config.fxCompositeMode.get())
                    .append(" (effective ").append(GpuCompat.compositeModeFor(config.fxCompositeMode.get(), safe)).append(")\n");
            text.append("Config: dynamic_lights.enabled=").append(config.dynamicLights.get())
                    .append(" shadow_mode=").append(config.lightShadowMode.get())
                    .append(" (effective ").append(GpuCompat.shadowModeFor(config.lightShadowMode.get(), safe)).append(")")
                    .append(" soft_shadows=").append(config.lightSoftShadows.get())
                    .append(" shadowed_lights=").append(config.lightShadowedLights.get())
                    .append(" resolution=").append(config.lightResolution.get())
                    .append(" volumetric=").append(config.volumetricLights.get()).append(" (effective ").append(config.volumetricLights.get() && !safe).append(")\n");
            text.append("Config: enable_bloom=").append(config.enableBloom.get()).append(" bloom_mip_level=").append(config.bloomMipLevel.get())
                    .append(" enable_custom_effects=").append(config.enableCustomEffects.get())
                    .append(" postfx_pool_budget_mb=").append(config.postFxPoolBudgetMB.get()).append('\n');
            text.append("Config: gpu_profile=").append(config.gpuProfile.get()).append(" gpu_sync=").append(config.gpuSync.get())
                    .append(" freeze_watchdog=").append(config.freezeWatchdog.get()).append('\n');
            text.append("Shader pack active: ").append(IrisCompat.isUsingShaderPack()).append('\n');
        } catch (Throwable t) {
            text.append("Config unavailable: ").append(t).append('\n');
        }
    }

    private static String photonVersion() {
        try {
            var container = net.minecraftforge.fml.ModList.get().getModContainerById(Photon.MOD_ID);
            return container.map(c -> c.getModInfo().getVersion().toString()).orElse("unknown");
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
