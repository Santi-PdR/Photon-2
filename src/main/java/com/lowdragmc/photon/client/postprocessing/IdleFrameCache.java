package com.lowdragmc.photon.client.postprocessing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Render-thread cache whose entries expire after a fixed number of unused frame boundaries. */
final class IdleFrameCache<K, V> {
    private static final class Entry<V> {
        final V value;
        long lastUsedFrame;

        Entry(V value, long lastUsedFrame) {
            this.value = value;
            this.lastUsedFrame = lastUsedFrame;
        }
    }

    private final int idleFrameLimit;
    private final Map<K, Entry<V>> entries = new HashMap<>();
    private long frame;

    IdleFrameCache(int idleFrameLimit) {
        if (idleFrameLimit < 0) throw new IllegalArgumentException("idleFrameLimit must not be negative");
        this.idleFrameLimit = idleFrameLimit;
    }

    V getOrCreate(K key, Supplier<? extends V> factory) {
        var entry = entries.get(key);
        if (entry == null) {
            entry = new Entry<>(Objects.requireNonNull(factory.get()), frame);
            entries.put(key, entry);
        } else {
            entry.lastUsedFrame = frame;
        }
        return entry.value;
    }

    /** Advance the frame clock and return values that expired at this boundary. */
    List<V> endFrame() {
        frame++;
        var expired = new ArrayList<V>();
        Iterator<Entry<V>> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (frame - entry.lastUsedFrame <= idleFrameLimit) continue;
            expired.add(entry.value);
            iterator.remove();
        }
        return List.copyOf(expired);
    }

    int size() {
        return entries.size();
    }
}
