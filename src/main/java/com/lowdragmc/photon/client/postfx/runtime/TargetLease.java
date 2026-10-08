package com.lowdragmc.photon.client.postfx.runtime;

import com.lowdragmc.photon.client.postprocessing.ResourceDisposal;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

/** Tracks pooled targets owned by one render operation and returns any untransferred targets on exit. */
final class TargetLease<T> implements AutoCloseable {
    private final Consumer<T> release;
    private final Set<T> owned = Collections.newSetFromMap(new IdentityHashMap<>());

    TargetLease(Consumer<T> release) {
        this.release = release;
    }

    void acquire(T target) {
        if (target != null) owned.add(target);
    }

    void release(T target) {
        if (owned.remove(target)) release.accept(target);
    }

    /** Transfer ownership to the frame-level consumer, which releases the target at frame end. */
    void transfer(T target) {
        owned.remove(target);
    }

    @Override
    public void close() {
        close(null);
    }

    void close(@Nullable Throwable operationFailure) {
        try {
            ResourceDisposal.disposeAllPreservingFailure(
                    new ArrayList<>(owned), release, operationFailure);
        } finally {
            owned.clear();
        }
    }
}
