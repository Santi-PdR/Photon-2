package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PhotonMeshCacheTest {
    @TempDir
    Path tempDir;

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

    @Test
    void pollingAnEditedModelFileInvalidatesItsCachedMesh() throws Exception {
        var cache = PhotonMeshCache.INSTANCE;
        var key = new PhotonMeshCache.ObjKey(
                ResourceLocation.fromNamespaceAndPath("photon_test", "edited.obj"), true);
        var file = tempDir.resolve("edited.obj");
        Files.writeString(file, "first version");
        var timestamp = Files.getLastModifiedTime(file);
        var generation = cache.generation();
        try {
            cache.trackFile(key, file.toFile());
            Files.writeString(file, "second version");
            Files.setLastModifiedTime(file, FileTime.fromMillis(timestamp.toMillis() + 5_000));

            cache.pollFileChanges();

            assertEquals(generation + 1, cache.generation());
        } finally {
            cache.invalidate(key);
        }
    }

    @Test
    void pollingAnyTrackedParentInvalidatesTheRootMesh() throws Exception {
        var cache = PhotonMeshCache.INSTANCE;
        var key = new PhotonMeshCache.JsonKey(ResourceLocation.fromNamespaceAndPath("photon_test", "parent-chain"));
        var rootFile = tempDir.resolve("root.json");
        var parentFile = tempDir.resolve("parent.json");
        Files.writeString(rootFile, "root");
        Files.writeString(parentFile, "parent");
        var timestamp = Files.getLastModifiedTime(parentFile);
        var attempts = new AtomicInteger();
        var generation = cache.generation();
        try {
            cache.trackFile(key, rootFile.toFile());
            cache.trackFile(key, parentFile.toFile());
            cache.get(key, ignored -> {
                attempts.incrementAndGet();
                return PhotonMesh.EMPTY;
            });

            Files.writeString(parentFile, "updated parent");
            Files.setLastModifiedTime(parentFile, FileTime.fromMillis(timestamp.toMillis() + 5_000));
            cache.pollFileChanges();

            assertEquals(generation + 1, cache.generation());
            cache.get(key, ignored -> {
                attempts.incrementAndGet();
                return PhotonMesh.EMPTY;
            });
            assertEquals(2, attempts.get());
        } finally {
            cache.invalidate(key);
        }
    }
}
