package com.lowdragmc.photon.client.fx;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadableValueCacheTest {
    @Test
    void staleLoadIsDiscardedAndRetriedAfterInvalidation() throws Exception {
        var cache = new ReloadableValueCache<String, String>();
        var started = new CountDownLatch(1);
        var finishOldLoad = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> cache.get("effect", () -> {
                if (attempts.getAndIncrement() == 0) {
                    started.countDown();
                    try {
                        if (!finishOldLoad.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("timed out waiting to release the old load");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    return "old resources";
                }
                return "reloaded resources";
            }));

            assertTrue(started.await(5, TimeUnit.SECONDS));
            cache.invalidate();
            finishOldLoad.countDown();

            assertEquals("reloaded resources", result.get(5, TimeUnit.SECONDS));
            assertEquals(2, attempts.get());
            assertEquals(1, cache.size());
        } finally {
            executor.shutdownNow();
        }
    }
}
