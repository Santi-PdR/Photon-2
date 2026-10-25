package com.lowdragmc.photon.client.util;

import com.lowdragmc.lowdraglib2.LDLib2;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/** Converts files selected in LDLib2's assets directory to resource locations. */
public final class PhotonAssetPath {
    private PhotonAssetPath() {
    }

    @Nullable
    public static ResourceLocation fromFile(Path assetsDirectory, Path filePath) {
        Path assetRoot = assetsDirectory.toAbsolutePath().normalize();
        Path selected = filePath.toAbsolutePath().normalize();
        if (!selected.startsWith(assetRoot)) return null;
        Path relative = assetRoot.relativize(selected);
        if (relative.getNameCount() < 2) return null;

        // Some LDLib2 versions expose the parent of the actual assets directory.
        int namespaceIndex = relative.getNameCount() >= 4
                && relative.getName(0).toString().equals("ldlib2")
                && relative.getName(1).toString().equals("assets") ? 2 : 0;
        if (relative.getNameCount() <= namespaceIndex + 1) return null;

        String namespace = relative.getName(namespaceIndex).toString();
        StringBuilder resourcePath = new StringBuilder();
        for (int i = namespaceIndex + 1; i < relative.getNameCount(); i++) {
            if (resourcePath.length() > 0) resourcePath.append('/');
            resourcePath.append(relative.getName(i));
        }
        String id = namespace + ":" + resourcePath;
        return LDLib2.isValidResourceLocation(id) ? new ResourceLocation(id) : null;
    }

    @Nullable
    public static ResourceLocation shaderLocationFromFile(Path assetsDirectory, Path filePath) {
        ResourceLocation assetLocation = fromFile(assetsDirectory, filePath);
        if (assetLocation == null) return null;
        String resourcePath = assetLocation.getPath();
        String prefix = "shaders/core/";
        int shaderIndex = resourcePath.indexOf(prefix);
        if (shaderIndex < 0 || !resourcePath.endsWith(".json")) return null;
        String shaderPath = resourcePath.substring(shaderIndex + prefix.length());
        if (shaderPath.length() <= ".json".length()) return null;
        String id = assetLocation.getNamespace() + ":"
                + shaderPath.substring(0, shaderPath.length() - ".json".length());
        return LDLib2.isValidResourceLocation(id) ? new ResourceLocation(id) : null;
    }

    @Nullable
    public static ResourceLocation modelLocationFromFile(Path assetsDirectory, Path filePath) {
        ResourceLocation assetLocation = fromFile(assetsDirectory, filePath);
        if (assetLocation == null) return null;
        String modelPath = assetLocation.getPath();
        String prefix = "models/";
        String suffix = ".json";
        if (!modelPath.startsWith(prefix) || !modelPath.endsWith(suffix)) return null;
        String locationPath = modelPath.substring(prefix.length(), modelPath.length() - suffix.length());
        if (locationPath.isEmpty()) return null;
        String id = assetLocation.getNamespace() + ":" + locationPath;
        return LDLib2.isValidResourceLocation(id) ? new ResourceLocation(id) : null;
    }
}

