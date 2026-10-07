package com.lowdragmc.photon.client.postprocessing;

import java.util.ArrayList;
import java.util.function.Consumer;

/** Attempts every release in a batch, then reports any cleanup failures together. */
final class ResourceDisposal {
    private ResourceDisposal() {}

    static <T> void disposeAll(Iterable<T> resources, Consumer<? super T> disposer) {
        Throwable failure = null;
        for (T resource : resources) {
            try {
                disposer.accept(resource);
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
                else if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
            }
        }
        if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
        if (failure instanceof Error errorFailure) throw errorFailure;
    }
}
