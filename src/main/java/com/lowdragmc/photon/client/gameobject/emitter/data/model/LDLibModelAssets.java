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
        var nested = findNested(location, pathWithinAssets);
        var fileId = new ResourceLocation(location.getNamespace(), pathWithinAssets.replace('\\', '/'));
        if (nested != null) {
            if (fileId.getPath().endsWith(".json")) adoptObjDependency(nested);
            return nested;
        }

        // Publish dependencies before the JSON itself. A workspace resource-pack reload can run
        // between two loose-file copies; Forge's OBJ geometry loader then sees the model JSON but
        // cannot resolve its referenced .obj and leaves a noisy, cached missing model. The Photon
        // mesh baker can read both files directly, but the resource manager also sees this JSON.
        if (fileId.getPath().endsWith(".json")) {
            Path looseJson = LooseTextures.find(fileId);
            if (looseJson != null) adoptObjDependency(looseJson);
        }
        LooseTextures.adoptIntoWorkspace(fileId);
        return findNested(location, pathWithinAssets);
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
