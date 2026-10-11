package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PhotonMeshCacheTest {
    @Test
    void retriesJsonMeshesWhenTheFirstBakeRunsDuringAResourceReload() {
        var cache = PhotonMeshCache.INSTANCE;
        var key = new PhotonMeshCache.JsonKey(ResourceLocation.fromNamespaceAndPath("photon_test", "reload-race"));
        var attempts = new AtomicInteger();
        try {
            assertSame(PhotonMesh.EMPTY, cache.get(key, ignored -> {
                attempts.incrementAndGet();
                return null;
            }));
            assertEquals(1, attempts.get());

            assertSame(PhotonMesh.EMPTY, cache.get(key, ignored -> {
                attempts.incrementAndGet();
                return PhotonMesh.EMPTY;
            }));
            assertEquals(2, attempts.get());
        } finally {
            cache.invalidate(key);
        }
    }
}
