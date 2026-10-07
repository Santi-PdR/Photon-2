package com.lowdragmc.photon.client.fx;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** A synchronous cache that discards values whose load crossed an invalidation. */
final class ReloadableValueCache<K, V> {
    private final Map<K, V> values = new HashMap<>();
    private long generation;

    V get(K key, Supplier<V> loader) {
        while (true) {
            long loadGeneration;
            synchronized (this) {
                var cached = values.get(key);
                if (cached != null) return cached;
                loadGeneration = generation;
            }

            var loaded = loader.get();
            synchronized (this) {
                if (generation != loadGeneration) continue;
                if (loaded == null) return null;
                return values.computeIfAbsent(key, ignored -> loaded);
            }
        }
    }

    synchronized int invalidate() {
        int count = values.size();
        values.clear();
        generation++;
        return count;
    }

    synchronized int size() {
        return values.size();
    }
}
