package com.lowdragmc.photon.client;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderVariantCacheTest {
    @Test
    void closesUniqueStaleVariantsButPreservesSharedBase() {
        var cache = new HashMap<String, Resource>();
        var sharedBase = new Resource();
        var stale = new Resource();
        cache.put("fallback", sharedBase);
        cache.put("tangent", stale);
        cache.put("other-key-same-instance", stale);
        var closes = new AtomicInteger();

        ShaderVariantCache.clearAndClose(cache, ignored -> closes.incrementAndGet(), sharedBase);

        assertTrue(cache.isEmpty());
        assertEquals(1, closes.get());
    }

    @Test
    void closesEveryCachedVariantWhenThereIsNoBaseShader() {
        var cache = new HashMap<String, Resource>();
        var first = new Resource();
        var second = new Resource();
        cache.put("first", first);
        cache.put("second", second);
        var closes = new AtomicInteger();

        ShaderVariantCache.clearAndClose(cache, ignored -> closes.incrementAndGet(), (Resource) null);

        assertTrue(cache.isEmpty());
        assertEquals(2, closes.get());
    }

    private static final class Resource {
    }
}
