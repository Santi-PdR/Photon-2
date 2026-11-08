package com.lowdragmc.photon.client.fx;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, best-effort reports for attempts that may otherwise fail without an exception. */
public final class FXDiagnostics {
    private static final int MAX_REPORTS = 128;
    private static final int MAX_OBJECTS = 128;
    private static final int MAX_CHARS = 256 * 1024;
    private static final Map<String, Long> LAST_WRITE = new LinkedHashMap<>();

    private FXDiagnostics() { }

    /** Called once per actual emit, not once per particle or frame. A start does not prove visibility. */
    public static void playback(FXData data, IEffectExecutor executor, int delay) {
        var location = executor instanceof FXEffectExecutor fxExecutor ? fxExecutor.getFx().getFxLocation() : null;
        var label = location != null ? location.toString()
                : executor instanceof com.lowdragmc.photon.gui.editor.FXProjectEffectExecutor editor
                ? editor.diagnosticLabel() : "embedded-or-unknown";
        var key = "play-" + label + "-" + data.objects().stream().map(o -> o.id().toString()).toList().hashCode();
        Photon.LOGGER.info("[fx-diagnostic] playback requested: fx={} executor={} objects={} delay={}; visibility not verified",
                label, executor.getClass().getSimpleName(), data.objects().size(), delay);
        var report = new StringBuilder("Photon playback diagnostic\nFX: ").append(label)
                .append("\nExecutor: ").append(executor.getClass().getName())
                .append("\nDelay ticks: ").append(delay).append("\nObjects: ").append(data.objects().size())
                .append("\nA playback request does not prove the effect was visible.\n");
        int count = 0;
        for (var object : data.objects()) {
            if (count++ >= MAX_OBJECTS || report.length() >= MAX_CHARS) break;
            report.append("\nObject: ").append(object.getName()).append(" type=")
                    .append(object.getClass().getSimpleName()).append(" id=").append(object.id())
                    .append(" enabled=").append(object.isEnabled())
                    .append(" parentId=").append(object.transform()._getInternalParentID()).append('\n');
            if (object instanceof ParticleEmitter particle) {
                report.append("duration=").append(particle.config.getDuration())
                        .append(" looping=").append(particle.config.isLooping())
                        .append(" maxParticles=").append(particle.config.getMaxParticles())
                        .append(" bursts=").append(particle.config.emission.getBursts().size()).append('\n');
            }
            try {
                var tag = object.serializeWrapper();
                if (tag != null) appendBounded(report, tag.toString());
            } catch (Exception e) {
                report.append("Could not serialize object: ").append(e).append('\n');
            }
            report.append('\n');
        }
        write(key, report.toString());
    }

    /** Reports a known load or decoding error. The saved NBT is limited and is never used to modify the source. */
    public static void failure(@Nullable ResourceLocation location, String stage, String reason,
                               @Nullable CompoundTag data, @Nullable Throwable error) {
        var label = location == null ? "unknown" : location.toString();
        Photon.LOGGER.warn("[fx-diagnostic] fx={} stage={} reason={}", label, stage, reason);
        var report = new StringBuilder("Photon FX diagnostic\nFX: ").append(label).append("\nStage: ")
                .append(stage).append("\nReason: ").append(reason).append('\n');
        if (error != null) {
            var trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            appendBounded(report, trace.toString());
        }
        if (data != null) {
            report.append("\nNBT (may be truncated):\n");
            appendBounded(report, data.toString());
        }
        write("failure-" + label + "-" + stage, report.toString());
    }

    private static void appendBounded(StringBuilder report, String text) {
        int remaining = Math.max(0, MAX_CHARS - report.length());
        report.append(text, 0, Math.min(text.length(), remaining));
        if (text.length() > remaining) report.append("\n[truncated]\n");
    }

    private static synchronized void write(String key, String text) {
        long now = System.currentTimeMillis();
        var previous = LAST_WRITE.get(key);
        if (previous != null && now - previous < 5000) return;
        if (previous == null && LAST_WRITE.size() >= MAX_REPORTS) {
            Photon.LOGGER.warn("[fx-diagnostic] report limit reached for this session; playback logging remains enabled");
            return;
        }
        try {
            Path directory = Platform.getGamePath().resolve("photon").resolve("diagnostics");
            Files.createDirectories(directory);
            Path path = directory.resolve("fx-" + Integer.toUnsignedString(key.hashCode(), 16) + ".txt");
            Files.writeString(path, text, StandardCharsets.UTF_8);
            LAST_WRITE.put(key, now);
            Photon.LOGGER.info("[fx-diagnostic] report: {}", path.toAbsolutePath());
        } catch (Exception e) {
            Photon.LOGGER.warn("[fx-diagnostic] could not write report for {}", key, e);
        }
    }
}
