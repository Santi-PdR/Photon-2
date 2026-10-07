package com.lowdragmc.photon.client.fx.timeline;

import java.util.HashMap;
import java.util.Map;

/** A small cache that rejects asynchronous results started before its last invalidation. */
final class ReloadableCache<K, V> {
    static final long NO_LOAD = -1L;

    private final Map<K, V> values = new HashMap<>();
    private final Map<K, Long> pending = new HashMap<>();
    private long generation;

    synchronized V get(K key) {
        return values.get(key);
    }

    synchronized long beginLoad(K key) {
        if (values.containsKey(key) || pending.getOrDefault(key, NO_LOAD) == generation) {
            return NO_LOAD;
        }
        pending.put(key, generation);
        return generation;
    }

    synchronized boolean complete(K key, long loadGeneration, V value) {
        Long pendingGeneration = pending.get(key);
        if (pendingGeneration == null || pendingGeneration != loadGeneration) {
            return false;
        }
        pending.remove(key);
        if (generation != loadGeneration) {
            return false;
        }
        values.put(key, value);
        return true;
    }

    synchronized void invalidate() {
        generation++;
        values.clear();
        pending.clear();
    }
}
