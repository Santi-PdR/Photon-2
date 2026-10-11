package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.lowdragmc.lowdraglib2.LDLib2;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves model files stored in LDLib2's nested {@code <namespace>/assets/<namespace>} tree. */
final class LDLibModelAssets {
    private LDLibModelAssets() {
    }

    static Path findNested(ResourceLocation location, String pathWithinAssets) {
        return findNested(LDLib2.getAssetsDir().toPath(), location, pathWithinAssets);
    }

    static Path findNested(Path assetsDir, ResourceLocation location, String pathWithinAssets) {
        Path normalizedAssetsDir = assetsDir.normalize();
        Path candidate = normalizedAssetsDir.resolve(location.getNamespace())
                .resolve("assets")
                .resolve(location.getNamespace())
                .resolve(pathWithinAssets)
                .normalize();
        if (!candidate.startsWith(normalizedAssetsDir) || !Files.isRegularFile(candidate)) {
            return null;
        }
        return candidate;
    }
}
