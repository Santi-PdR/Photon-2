package com.lowdragmc.photon.client.virtuallevel;

import com.lowdragmc.lowdraglib2.client.scene.ParticleManager;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.lowdragmc.photon.Photon;

/**
 * The virtual level behind the FX editor's scene.
 *
 * <p>LDLib2's {@code DummyWorld.tickWorld()} posts Forge's {@code LevelTickEvent} for the virtual level before
 * and after ticking it, which hands the editor to every other mod's {@code LevelTickEvent} listener (see
 * {@link VirtualLevelTickGuard} for the crash that produced this class). This level ticks the same things —
 * entities, block entities and the scene's particle manager — without the events, so the editor's own scene
 * is safe even when {@code DummyWorldMixin} could not apply to the LDLib2 build in use.</p>
 *
 * <p>Third-party code can still run inside that tick: the "real world" scene mode copies the blocks around the
 * player into this level, block entities and all, so another mod's ticker may execute against a level it never
 * expected. A failure there used to be a "Ticking screen" crash of the whole game. Each phase is therefore
 * guarded: the failed phase is skipped for that tick, the editor keeps running, and the failure is logged
 * once in full and then every {@value #REPORT_INTERVAL} failures with the frame that most likely owns it.</p>
 */
public class EditorSceneLevel extends TrackedDummyWorld {
    /** A persistent fault is logged again after this many failed phases (about ten seconds at twenty ticks per second). */
    public static final int REPORT_INTERVAL = 200;
    private static final String[] PLATFORM_PREFIXES = {
            "java.", "jdk.", "sun.", "com.mojang.", "net.minecraft.", "net.minecraftforge.", "cpw.mods.",
            "org.spongepowered.", "com.lowdragmc."
    };

    private final String label;
    private long failedPhases;
    private long lastReportedFailure;

    /** @param label names the scene in the log, e.g. {@code editor.scene}. */
    public EditorSceneLevel(String label) {
        this.label = label;
    }

    /** Same work as {@code DummyWorld.tickWorld()}, minus Forge's {@code LevelTickEvent} pair, with each phase guarded. */
    @Override
    public void tickWorld() {
        guarded("entity", this::tickEntities);
        ParticleManager particleManager = getParticleManager();
        if (particleManager != null) {
            guarded("particle", particleManager::tick);
        }
    }

    /** Number of phases that failed so far; stays 0 in a healthy editor. */
    public long failedPhases() {
        return failedPhases;
    }

    private void guarded(String phase, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException | LinkageError failure) {
            report(phase, failure);
        }
    }

    private void report(String phase, Throwable failure) {
        failedPhases++;
        if (failedPhases != 1 && failedPhases - lastReportedFailure < REPORT_INTERVAL) {
            return;
        }
        lastReportedFailure = failedPhases;
        Photon.LOGGER.error("[scene-tick] the {} tick of the editor scene '{}' failed ({} failed phase(s) so far; probable origin: {}); the editor keeps running and skipped that work for this tick",
                phase, label, failedPhases, describeOrigin(failure), failure);
    }

    /**
     * The first stack frame of the failure (or of one of its causes) that belongs neither to the platform nor to
     * Photon/LDLib2, with the module that owns it — the quickest pointer to the mod that broke in the editor.
     * Falls back to the top frame of the root cause when the failure is entirely our own.
     */
    static String describeOrigin(Throwable failure) {
        Throwable root = failure;
        for (Throwable current = failure; current != null; current = current.getCause() == current ? null : current.getCause()) {
            root = current;
            for (StackTraceElement frame : current.getStackTrace()) {
                String owner = frame.getClassName();
                if (!isPlatform(owner)) {
                    return frameName(frame) + " (" + moduleOf(owner) + ")";
                }
            }
        }
        StackTraceElement[] trace = root.getStackTrace();
        return trace.length == 0 ? root.getClass().getName() : "no third-party frame, top frame " + frameName(trace[0]);
    }

    private static String frameName(StackTraceElement frame) {
        return frame.getClassName() + "." + frame.getMethodName() + (frame.getLineNumber() > 0 ? ":" + frame.getLineNumber() : "");
    }

    private static boolean isPlatform(String owner) {
        for (String prefix : PLATFORM_PREFIXES) {
            if (owner.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String moduleOf(String owner) {
        try {
            Module module = Class.forName(owner, false, EditorSceneLevel.class.getClassLoader()).getModule();
            return module.isNamed() ? "module " + module.getName() : "unnamed module";
        } catch (Throwable notLoadable) {
            return "module unknown";
        }
    }
}
