package com.lowdragmc.photon.client.postprocessing;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class IdleFrameCacheTest {
    @Test
    void reusesEntriesPerViewportKey() {
        var cache = new IdleFrameCache<String, Object>(120);
        var creations = new AtomicInteger();
        Object world = cache.getOrCreate("1920x1080", () -> {
            creations.incrementAndGet();
            return new Object();
        });
        assertSame(world, cache.getOrCreate("1920x1080", Object::new));
        Object editor = cache.getOrCreate("640x360", Object::new);

        assertEquals(2, cache.size());
        assertEquals(1, creations.get());
        assertEquals(java.util.List.of(), cache.endFrame());
        assertSame(world, cache.getOrCreate("1920x1080", Object::new));
        assertSame(editor, cache.getOrCreate("640x360", Object::new));
    }

    @Test
    void expiresOnlyAfterMoreThanTheIdleFrameLimit() {
        var cache = new IdleFrameCache<String, Object>(120);
        Object target = cache.getOrCreate("viewport", Object::new);

        for (int i = 0; i < 120; i++) assertEquals(java.util.List.of(), cache.endFrame());
        assertEquals(1, cache.size());
        assertEquals(java.util.List.of(target), cache.endFrame());
        assertEquals(0, cache.size());
    }

    @Test
    void reuseRefreshesTheIdleLease() {
        var cache = new IdleFrameCache<String, Object>(120);
        Object target = cache.getOrCreate("viewport", Object::new);
        for (int i = 0; i < 119; i++) cache.endFrame();
        assertSame(target, cache.getOrCreate("viewport", Object::new));

        for (int i = 0; i < 120; i++) assertEquals(java.util.List.of(), cache.endFrame());
        assertEquals(java.util.List.of(target), cache.endFrame());
    }
}
