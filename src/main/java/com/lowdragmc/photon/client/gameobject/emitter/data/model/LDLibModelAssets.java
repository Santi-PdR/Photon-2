package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.photon.client.fx.LooseTextures;
import com.lowdragmc.photon.client.util.PhotonPaths;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
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
        if (location == null || pathWithinAssets == null) return null;
        var normalizedPath = pathWithinAssets.replace('\\', '/');
        var fileId = ResourceLocation.tryParse(location.getNamespace() + ":" + normalizedPath);
        if (!PhotonPaths.isSafeAssetLocation(fileId)) return null;
        var nested = findNested(location, fileId.getPath());
        if (nested != null) {
            if (fileId.getPath().endsWith(".json")) adoptObjDependency(nested);
            else if (fileId.getPath().endsWith(".obj")) adoptObjAndMaterials(fileId);
            return nested;
        }

        // Publish dependencies before the JSON itself. A workspace resource-pack reload can run
        // between two loose-file copies; Forge's OBJ geometry loader then sees the model JSON but
        // cannot resolve its referenced .obj and leaves a noisy, cached missing model. The Photon
        // mesh baker can read both files directly, but the resource manager also sees this JSON.
        if (fileId.getPath().endsWith(".json")) {
            Path looseJson = LooseTextures.find(fileId);
            if (looseJson != null) adoptObjDependency(looseJson);
            LooseTextures.adoptIntoWorkspace(fileId);
        } else if (fileId.getPath().endsWith(".obj")) {
            adoptObjAndMaterials(fileId);
        } else {
            LooseTextures.adoptIntoWorkspace(fileId);
        }
        return findNested(location, pathWithinAssets);
    }

    /** Resolve and watch external glTF buffers alongside the model that consumes them. */
    static void trackGltfBuffers(Object cacheKey, ResourceLocation model, byte[] bytes) throws java.io.IOException {
        for (var dependency : com.lowdragmc.photon.client.fx.fxpack.GltfAssetReferences.buffers(model, bytes)) {
            PhotonMeshCache.INSTANCE.trackAssetFile(cacheKey, dependency);
            var loose = LooseTextures.find(dependency);
            if (loose != null) PhotonMeshCache.INSTANCE.trackFile(cacheKey, loose.toFile());
            var nested = resolve(dependency, dependency.getPath());
            if (nested != null) PhotonMeshCache.INSTANCE.trackFile(cacheKey, nested.toFile());
        }
    }

    private static void adoptObjDependency(Path jsonFile) {
        try (BufferedReader reader = Files.newBufferedReader(jsonFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            String loader = json.has("loader") ? json.get("loader").getAsString() : "";
            if (!loader.equals("forge:obj") && !loader.equals("neoforge:obj")) return;
            if (!json.has("model") || !json.get("model").isJsonPrimitive()) return;
            adoptObjAndMaterials(new ResourceLocation(json.get("model").getAsString()));
        } catch (Exception ignored) {
            // The normal JSON/model loader reports malformed input; loose-file adoption remains best-effort.
        }
    }

    /** Adopt OBJ sidecar material libraries before the OBJ itself can become visible to a pack reload. */
    private static void adoptObjAndMaterials(ResourceLocation objLocation) {
        Path looseObj = LooseTextures.find(objLocation);
        if (looseObj == null) {
            LooseTextures.adoptIntoWorkspace(objLocation);
            return;
        }
        try {
            Path modelDirectory = Path.of(objLocation.getPath()).getParent();
            if (modelDirectory != null) {
                try (var reader = Files.newBufferedReader(looseObj)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.strip();
                        if (!trimmed.startsWith("mtllib ")) continue;
                        // Forge's OBJ parser treats the first token after mtllib as the library path.
                        String library = trimmed.substring("mtllib ".length()).strip().split("\\s+", 2)[0]
                                .replace('\\', '/');
                        ResourceLocation materialLocation;
                        if (library.contains(":")) {
                            materialLocation = new ResourceLocation(library);
                        } else {
                            Path materialPath = modelDirectory.resolve(library).normalize();
                            // MTL paths are relative to the OBJ folder; never let a loose sidecar escape its namespace.
                            if (materialPath.isAbsolute() || materialPath.startsWith("..")) continue;
                            materialLocation = new ResourceLocation(objLocation.getNamespace(),
                                    materialPath.toString().replace('\\', '/'));
                        }
                        Path resourcePath = Path.of(materialLocation.getPath()).normalize();
                        if (resourcePath.isAbsolute() || resourcePath.startsWith("..")) continue;
                        LooseTextures.adoptIntoWorkspace(materialLocation);
                    }
                }
            }
        } catch (Exception ignored) {
            // A malformed or inaccessible sidecar must not prevent the model or effect from loading.
        }
        // OBJ is copied last: any mtllib it names is already in place if the resource manager reloads now.
        LooseTextures.adoptIntoWorkspace(objLocation);
    }

    static Path findNested(Path assetsDir, ResourceLocation location, String pathWithinAssets) {
        if (location == null || pathWithinAssets == null) return null;
        var normalizedPath = pathWithinAssets.replace('\\', '/');
        var fileId = ResourceLocation.tryParse(location.getNamespace() + ":" + normalizedPath);
        if (!PhotonPaths.isSafeAssetLocation(fileId)) return null;
        Path root = assetsDir.toAbsolutePath().normalize();
        Path canonicalRoot;
        try {
            canonicalRoot = root.toRealPath();
        } catch (java.io.IOException | SecurityException inaccessibleRoot) {
            return null;
        }
        // Nested editor tree first: a resource-pack file of the same id is allowed to exist and
        // must not win (see ci_nested_obj). Then the pack layouts Photon actually writes.
        Path[] candidates = {
                root.resolve(location.getNamespace()).resolve("assets").resolve(location.getNamespace()).resolve(fileId.getPath()),
                root.resolve(location.getNamespace()).resolve(fileId.getPath()),
                root.resolve("assets").resolve(location.getNamespace()).resolve(fileId.getPath())
        };
        for (var candidate : candidates) {
            var normalized = candidate.normalize();
            if (!normalized.startsWith(root)) continue;
            try {
                if (!Files.isRegularFile(normalized)) continue;
                var canonical = normalized.toRealPath();
                if (canonical.startsWith(canonicalRoot)) return canonical;
            } catch (java.io.IOException | SecurityException ignored) {
                // Ignore missing or unreadable candidates and continue through lower-priority layouts.
            }
        }
        return null;
    }
}
