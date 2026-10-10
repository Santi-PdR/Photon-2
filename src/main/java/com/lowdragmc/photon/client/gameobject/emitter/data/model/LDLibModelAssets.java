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
        Path assetsDir = LDLib2.getAssetsDir().toPath();
        Path candidate = assetsDir.resolve(location.getNamespace())
                .resolve("assets")
                .resolve(location.getNamespace())
                .resolve(pathWithinAssets)
                .normalize();
        if (!candidate.startsWith(assetsDir.normalize()) || !Files.isRegularFile(candidate)) {
            return null;
        }
        return candidate;
    }
}
