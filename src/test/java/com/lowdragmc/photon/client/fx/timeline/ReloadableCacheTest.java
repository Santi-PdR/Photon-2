package com.lowdragmc.photon.client.fx.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadableCacheTest {
    @Test
    void cachesValidZeroAndAllowsReloadToStartANewLoad() {
        var cache = new ReloadableCache<String, Double>();
        var generation = cache.beginLoad("sound");

        assertNotEquals(ReloadableCache.NO_LOAD, generation);
        assertEquals(ReloadableCache.NO_LOAD, cache.beginLoad("sound"));
        assertTrue(cache.complete("sound", generation, 0.0));
        assertEquals(0.0, cache.get("sound"));
        assertEquals(ReloadableCache.NO_LOAD, cache.beginLoad("sound"));

        cache.invalidate();
        assertNull(cache.get("sound"));
        assertNotEquals(ReloadableCache.NO_LOAD, cache.beginLoad("sound"));
    }

    @Test
    void staleCompletionCannotOverwriteOrClearThePostReloadLoad() {
        var cache = new ReloadableCache<String, Double>();
        var oldGeneration = cache.beginLoad("sound");
        cache.invalidate();
        var newGeneration = cache.beginLoad("sound");

        assertFalse(cache.complete("sound", oldGeneration, 12.0));
        assertNull(cache.get("sound"));
        assertEquals(ReloadableCache.NO_LOAD, cache.beginLoad("sound"));
        assertTrue(cache.complete("sound", newGeneration, 8.0));
        assertEquals(8.0, cache.get("sound"));
    }
}
