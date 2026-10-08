package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld;
import net.minecraft.world.level.Level;

/**
 * Marks the Photon editor's virtual scene tick so it does not look like a real world tick to
 * unrelated Forge event subscribers.
 */
public final class PhotonSceneTickContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private PhotonSceneTickContext() {
    }

    public static Scope suppressVirtualLevelEvents() {
        DEPTH.set(DEPTH.get() + 1);
        return new Scope();
    }

    public static boolean shouldSuppress(Level level) {
        return DEPTH.get() > 0 && level instanceof DummyWorld;
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            int depth = DEPTH.get();
            if (depth <= 1) {
                DEPTH.remove();
            } else {
                DEPTH.set(depth - 1);
            }
        }
    }
}
