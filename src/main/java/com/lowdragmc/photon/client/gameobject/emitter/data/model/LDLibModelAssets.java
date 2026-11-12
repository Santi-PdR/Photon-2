package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.photon.client.fx.LooseTextures;
import com.lowdragmc.photon.client.util.PhotonPaths;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves editable models from the Photon workspace first, then LDLib2.
 * Nested editor layout wins over a resource-pack file of the same id.
 * Loose files that only {@link LooseTextures} can see are copied into the Photon
 * workspace so the JSON baker, OBJ loader and glTF loader all find the same mesh.
 */
final class LDLibModelAssets {
    private LDLibModelAssets() {
    }

    static Path findNested(ResourceLocation location, String pathWithinAssets) {
        var photon = findNested(PhotonPaths.getAssetsDir().toPath(), location, pathWithinAssets);
        return photon != null ? photon : findNested(LDLib2.getAssetsDir().toPath(), location, pathWithinAssets);
    }

    /**
     * Disk path for this model id. When the three editor layouts do not already
     * contain it, a loose copy is adopted into the Photon workspace and the
     * lookup is retried.
     */
    @Nullable
    static Path resolve(ResourceLocation location, String pathWithinAssets) {
        var nested = findNested(location, pathWithinAssets);
        if (nested != null) return nested;
        var fileId = new ResourceLocation(location.getNamespace(), pathWithinAssets.replace('\\', '/'));
        LooseTextures.adoptIntoWorkspace(fileId);
        return findNested(location, pathWithinAssets);
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
