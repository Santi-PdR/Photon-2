package com.lowdragmc.photon.client.util;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/** Maps files in LDLib2's asset directory to resource locations. */
public final class PhotonAssetPath {
    private PhotonAssetPath() {
    }

    @Nullable
    public static ResourceLocation fromFile(Path assetsDirectory, Path filePath) {
        var assetRoot = assetsDirectory.toAbsolutePath().normalize();
        var selected = filePath.toAbsolutePath().normalize();
        if (!selected.startsWith(assetRoot)) return null;

        var relative = assetRoot.relativize(selected);
        if (relative.getNameCount() < 2) return null;

        // LDLib2 may expose assets under <assets>/ldlib2/assets/<namespace>/....
        int namespaceIndex = relative.getNameCount() >= 4
                && relative.getName(0).toString().equals("ldlib2")
                && relative.getName(1).toString().equals("assets") ? 2 : 0;
        if (relative.getNameCount() <= namespaceIndex + 1) return null;

        var namespace = relative.getName(namespaceIndex).toString();
        var path = new StringBuilder();
        for (int i = namespaceIndex + 1; i < relative.getNameCount(); i++) {
            if (path.length() > 0) path.append('/');
            path.append(relative.getName(i));
        }
        return ResourceLocation.tryParse(namespace + ":" + path);
    }

    @Nullable
    public static ResourceLocation shaderLocationFromFile(Path assetsDirectory, Path filePath) {
        var assetLocation = fromFile(assetsDirectory, filePath);
        if (assetLocation == null) return null;

        var resourcePath = assetLocation.getPath();
        var shaderPrefix = "shaders/core/";
        var shaderIndex = resourcePath.indexOf(shaderPrefix);
        if (shaderIndex < 0 || !resourcePath.endsWith(".json")) return null;

        var shaderPath = resourcePath.substring(shaderIndex + shaderPrefix.length());
        if (shaderPath.length() <= 5) return null;
        return ResourceLocation.tryBuild(assetLocation.getNamespace(),
                shaderPath.substring(0, shaderPath.length() - 5));
    }

    @Nullable
    public static ResourceLocation modelLocationFromFile(Path assetsDirectory, Path filePath) {
        var assetLocation = fromFile(assetsDirectory, filePath);
        if (assetLocation == null) return null;

        var modelPath = assetLocation.getPath();
        var modelPrefix = "models/";
        if (!modelPath.startsWith(modelPrefix) || !modelPath.endsWith(".json")) return null;
        var locationPath = modelPath.substring(modelPrefix.length(), modelPath.length() - ".json".length());
        return locationPath.isEmpty() ? null
                : ResourceLocation.tryBuild(assetLocation.getNamespace(), locationPath);
    }
}
