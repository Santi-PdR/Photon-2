package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.photon.client.util.PhotonPaths;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves editable models from Photon first, then LDLib2's legacy per-namespace assets folder. */
final class LDLibModelAssets {
    private LDLibModelAssets() {
    }

    static Path findNested(ResourceLocation location, String pathWithinAssets) {
        var photon = findNested(PhotonPaths.getAssetsDir().toPath(), location, pathWithinAssets);
        return photon != null ? photon : findNested(LDLib2.getAssetsDir().toPath(), location, pathWithinAssets);
    }

    static Path findNested(Path assetsDir, ResourceLocation location, String pathWithinAssets) {
        Path root = assetsDir.toAbsolutePath().normalize();
        // Nested editor tree first: a resource-pack file of the same id is allowed to exist and
        // must not win (see ci_nested_obj). Then the pack layouts Photon actually writes.
        Path[] candidates = {
                root.resolve(location.getNamespace()).resolve("assets").resolve(location.getNamespace()).resolve(pathWithinAssets),
                root.resolve(location.getNamespace()).resolve(pathWithinAssets),
                root.resolve("assets").resolve(location.getNamespace()).resolve(pathWithinAssets)
        };
        for (var candidate : candidates) {
            var normalized = candidate.normalize();
            if (normalized.startsWith(root) && Files.isRegularFile(normalized)) {
                return normalized;
            }
        }
        return null;
    }
}
