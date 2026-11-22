package com.lowdragmc.photon.client.virtuallevel;

import com.lowdragmc.photon.Photon;
import net.minecraft.world.level.Level;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps Forge's {@code LevelTickEvent} away from LDLib2's virtual levels.
 *
 * <p>Every LDLib2 {@code Scene} ticks its {@code TrackedDummyWorld} once per screen tick (the {@code Scene}
 * constructor enables {@code tickWorld}), and {@code DummyWorld.tickWorld()} brackets that tick with
 * {@code ForgeEventFactory.onPreLevelTick}/{@code onPostLevelTick}: a {@code TickEvent.LevelTickEvent} whose
 * {@code level} is a {@code DummyWorld} (a plain {@code Level} subclass flagged client side) goes to every
 * mod on the instance. Listeners written for real worlds cast {@code event.level} to {@code ClientLevel}
 * without an {@code instanceof}, and the first one on the bus takes the whole screen down:</p>
 *
 * <pre>
 * java.lang.ClassCastException: com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld cannot be cast to net.minecraft.client.multiplayer.ClientLevel
 *     at net.goo.brutality.event.forge.DelayedTaskScheduler.onLevelTick(DelayedTaskScheduler.java:51)   (Brutality 0.8.7)
 *     at net.minecraftforge.event.ForgeEventFactory.onPostLevelTick(ForgeEventFactory.java:955)
 *     at com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld.tickWorld(DummyWorld.java:321)
 *     at com.lowdragmc.lowdraglib2.gui.ui.elements.Scene.screenTick(Scene.java:229)
 *     at com.lowdragmc.photon.gui.editor.view.scene.SceneView.screenTick(SceneView.java:192)   -- "Ticking screen" crash of the FX editor
 * </pre>
 *
 * <p>Nothing in Photon, LDLib2 or KilaGraph listens for a virtual level's tick, so the events carry no
 * information anyone wants and only expose the editor to every other mod's assumptions. Two layers keep
 * them off the bus: {@code DummyWorldMixin} redirects both {@code ForgeEventFactory} calls inside
 * {@code DummyWorld.tickWorld()} to {@link #suppressed(Level)} (covers every LDLib2 scene, including ones
 * Photon does not build itself), and {@link EditorSceneLevel} — the level behind the FX editor's scene —
 * overrides {@code tickWorld()} so the editor never depends on the mixin having applied. The counter here
 * lets the client smoke test prove the first layer is live.</p>
 */
public final class VirtualLevelTickGuard {
    private static final AtomicLong SUPPRESSED = new AtomicLong();
    private static volatile boolean announced;

    private VirtualLevelTickGuard() {
    }

    /** Called by {@code DummyWorldMixin} in place of each {@code ForgeEventFactory.on(Pre|Post)LevelTick} call. */
    public static void suppressed(Level level) {
        SUPPRESSED.incrementAndGet();
        if (!announced) {
            announced = true;
            Photon.LOGGER.info("[virtual-level-tick] {} is ticking inside a screen; Photon keeps Forge's LevelTickEvent from being posted for LDLib2 virtual levels, because listeners that cast event.level to ClientLevel or ServerLevel would crash the screen",
                    level.getClass().getName());
        }
    }

    /** How many virtual-level tick events were kept off the Forge bus so far (two per ticked virtual level per tick). */
    public static long suppressedCount() {
        return SUPPRESSED.get();
    }
}
