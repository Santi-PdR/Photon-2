package com.lowdragmc.photon.client.postprocessing;

import java.util.HashMap;
import java.util.Map;

/** Per-key retry windows for render resources that may become allocatable after a resize/reload. */
final class FrameRetryPolicy<K> {
    private final Map<K, Long> retryAtFrame = new HashMap<>();

    boolean shouldDefer(K key, long frame) {
        return retryAtFrame.getOrDefault(key, Long.MIN_VALUE) > frame;
    }

    void failed(K key, long frame, int retryFrames) {
        if (retryFrames <= 0) throw new IllegalArgumentException("retryFrames must be positive");
        retryAtFrame.put(key, frame + retryFrames);
    }

    void succeeded(K key) {
        retryAtFrame.remove(key);
    }

    void advanceTo(long frame) {
        retryAtFrame.values().removeIf(retryAt -> retryAt <= frame);
    }
}
