package com.lowdragmc.photon.core;

import com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld;
import net.minecraft.world.level.Level;

/**
 * Marks the Photon editor's virtual scene tick so it does not look like a real world tick to
 * unrelated Forge event subscribers.
 */
public final class PhotonSceneTickContext {
    private static final ThreadLocal<DummyWorld> ACTIVE_LEVEL = new ThreadLocal<>();

    private PhotonSceneTickContext() {
    }

    public static Scope suppressVirtualLevelEvents(DummyWorld level) {
        DummyWorld previous = ACTIVE_LEVEL.get();
        ACTIVE_LEVEL.set(level);
        return new Scope(previous);
    }

    public static boolean shouldSuppress(Level level) {
        return level == ACTIVE_LEVEL.get() && level instanceof DummyWorld;
    }

    public static final class Scope implements AutoCloseable {
        private final DummyWorld previous;
        private boolean closed;

        private Scope(DummyWorld previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) {
                ACTIVE_LEVEL.remove();
            } else {
                ACTIVE_LEVEL.set(previous);
            }
        }
    }
}
