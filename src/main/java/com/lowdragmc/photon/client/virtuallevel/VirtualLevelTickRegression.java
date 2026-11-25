package com.lowdragmc.photon.client.virtuallevel;

import com.lowdragmc.lowdraglib2.client.scene.ParticleManager;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.lowdragmc.photon.Photon;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pins, inside the running client, that no LDLib2 virtual level reaches Forge's {@code LevelTickEvent} listeners
 * and that the editor's scene survives a tick that fails in foreign code (see {@link VirtualLevelTickGuard}).
 * The probe listener is written the way the crashing one was — it casts {@code event.level} to {@code ClientLevel}
 * whenever the level says it is client side — so a regression reproduces the original failure instead of a
 * weaker proxy for it. Runs inside the client smoke test with the FX editor open.
 */
public final class VirtualLevelTickRegression {
    private VirtualLevelTickRegression() {
    }

    public static void verify(TrackedDummyWorld editorLevel) {
        check(editorLevel instanceof EditorSceneLevel, "the FX editor's scene does not use EditorSceneLevel: " + editorLevel.getClass().getName());
        List<Level> virtualLevelsSeen = new ArrayList<>();
        Consumer<TickEvent.LevelTickEvent> castingListener = event -> {
            if (event.level instanceof DummyWorld) {
                virtualLevelsSeen.add(event.level);
            }
            if (event.level.isClientSide) {
                ClientLevel assumedClientLevel = (ClientLevel) event.level; // Brutality 0.8.7, DelayedTaskScheduler.onLevelTick
                assumedClientLevel.getClass();
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.LevelTickEvent.class, castingListener);
        try {
            // 1. the editor's level class: three world ticks reach no listener, even without the mixin
            EditorSceneLevel editorStyle = new EditorSceneLevel("regression");
            for (int i = 0; i < 3; i++) {
                editorStyle.tickWorld();
            }
            check(virtualLevelsSeen.isEmpty(), "EditorSceneLevel.tickWorld() still posts LevelTickEvent");
            check(editorStyle.failedPhases() == 0, "a healthy EditorSceneLevel tick reported " + editorStyle.failedPhases() + " failed phase(s)");

            // 2. any other LDLib2 virtual level (mesh previews, LDLib2's own renderer previews): the mixin keeps the events off the bus
            long suppressedBefore = VirtualLevelTickGuard.suppressedCount();
            TrackedDummyWorld plainLdlib2Level = new TrackedDummyWorld();
            try {
                plainLdlib2Level.tickWorld();
            } catch (RuntimeException leaked) {
                throw new IllegalStateException("a plain TrackedDummyWorld still posts LevelTickEvent to listeners that cast event.level (DummyWorldMixin did not apply)", leaked);
            }
            check(virtualLevelsSeen.isEmpty(), "a plain TrackedDummyWorld still posts LevelTickEvent (DummyWorldMixin did not apply)");
            long suppressed = VirtualLevelTickGuard.suppressedCount() - suppressedBefore;
            check(suppressed == 2, "DummyWorldMixin redirected " + suppressed + " LevelTickEvent call(s) for one virtual level tick instead of 2");

            // 3. a phase that fails in foreign code is skipped and reported; the tick itself never throws
            EditorSceneLevel faulty = new EditorSceneLevel("regression-faulty");
            faulty.setParticleManager(new ParticleManager() {
                @Override
                public void tick() {
                    throw new IllegalStateException("injected third-party failure inside the editor scene tick");
                }
            });
            for (int i = 0; i < 3; i++) {
                faulty.tickWorld();
            }
            check(faulty.failedPhases() == 3, "the guarded tick counted " + faulty.failedPhases() + " failed phase(s) instead of 3");

            // 4. the report names the first frame outside the platform and Photon/LDLib2
            Throwable foreign = new IllegalStateException("probe");
            foreign.setStackTrace(new StackTraceElement[]{
                    new StackTraceElement("net.minecraft.world.level.Level", "tickBlockEntities", "Level.java", 1),
                    new StackTraceElement("com.example.thirdparty.TickListener", "onLevelTick", "TickListener.java", 51),
                    new StackTraceElement("com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld", "tickWorld", "DummyWorld.java", 321)
            });
            String origin = EditorSceneLevel.describeOrigin(foreign);
            check(origin.startsWith("com.example.thirdparty.TickListener.onLevelTick:51 ("), "the failure report does not point at the foreign frame: " + origin);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(castingListener);
        }
        Photon.LOGGER.info("[virtual-level-tick-smoke] LDLib2 virtual levels post no Forge LevelTickEvent while a listener casts event.level to ClientLevel ({} redirected so far), and the editor scene survives and reports a failing tick",
                VirtualLevelTickGuard.suppressedCount());
    }

    private static void check(boolean condition, String failure) {
        if (!condition) {
            throw new IllegalStateException(failure);
        }
    }
}
