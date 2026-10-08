package com.lowdragmc.photon.client.postprocessing;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.Consumer;

/** Attempts every release in a batch, then reports any cleanup failures together. */
public final class ResourceDisposal {
    private ResourceDisposal() {}

    /** Run one cleanup while retaining an earlier operation failure as the primary exception. */
    public static void cleanupAfterFailure(Throwable failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error cleanupFailure) {
            if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
        }
    }

    static <T> void disposeAll(Iterable<T> resources, Consumer<? super T> disposer) {
        disposeAllPreservingFailure(resources, disposer, null);
    }

    /** Attempt every release, attaching failures to an earlier operation failure when present. */
    public static <T> void disposeAllPreservingFailure(Iterable<T> resources,
                                                        Consumer<? super T> disposer,
                                                        @Nullable Throwable operationFailure) {
        Throwable failure = null;
        for (T resource : resources) {
            try {
                disposer.accept(resource);
            } catch (RuntimeException | Error cleanupFailure) {
                if (operationFailure != null) {
                    if (operationFailure != cleanupFailure) operationFailure.addSuppressed(cleanupFailure);
                } else if (failure == null) {
                    failure = cleanupFailure;
                } else if (failure != cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }
        if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
        if (failure instanceof Error errorFailure) throw errorFailure;
    }
}
