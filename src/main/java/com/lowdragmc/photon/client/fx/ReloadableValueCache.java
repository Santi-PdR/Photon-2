package com.lowdragmc.photon.client.fx;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/** A synchronous cache that discards values whose load crossed an invalidation. */
final class ReloadableValueCache<K, V> {
    private final Map<K, V> values = new HashMap<>();
    private final Map<K, Pending<V>> pending = new HashMap<>();
    private long generation;

    V get(K key, Supplier<V> loader) {
        while (true) {
            Pending<V> load;
            boolean owner;
            synchronized (this) {
                var cached = values.get(key);
                if (cached != null) return cached;
                load = pending.get(key);
                if (load != null && load.generation == generation) {
                    owner = false;
                } else {
                    load = new Pending<>(generation);
                    pending.put(key, load);
                    owner = true;
                }
            }

            if (owner) {
                V loaded;
                try {
                    loaded = loader.get();
                } catch (RuntimeException | Error failure) {
                    synchronized (this) {
                        pending.remove(key, load);
                        if (generation != load.generation) {
                            load.result.complete(null);
                            continue;
                        }
                        load.result.completeExceptionally(failure);
                    }
                    throw failure;
                }
                synchronized (this) {
                    pending.remove(key, load);
                    if (generation != load.generation) {
                        load.result.complete(null);
                        continue;
                    }
                    if (loaded != null) values.put(key, loaded);
                    load.result.complete(loaded);
                    return loaded;
                }
            }

            try {
                var loaded = load.result.get();
                synchronized (this) {
                    if (generation != load.generation) continue;
                    return loaded;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for a cached value", e);
            } catch (ExecutionException e) {
                var cause = e.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("cached value load failed", cause);
            }
        }
    }

    synchronized int invalidate() {
        int count = values.size();
        values.clear();
        generation++;
        pending.values().forEach(load -> load.result.complete(null));
        pending.clear();
        return count;
    }

    synchronized int size() {
        return values.size();
    }

    private static final class Pending<V> {
        private final long generation;
        private final CompletableFuture<V> result = new CompletableFuture<>();

        private Pending(long generation) {
            this.generation = generation;
        }
    }
}
