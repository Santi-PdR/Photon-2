package com.lowdragmc.photon.client.util;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/** Converts selected files under a conventional assets directory into resource identifiers. */
public final class PhotonAssetPath {
    private PhotonAssetPath() {}

    @Nullable
    public static ResourceLocation fromFile(Path preferredAssetsRoot, Path filePath) {
        Path selected = filePath.toAbsolutePath().normalize();
        Path root = preferredAssetsRoot.toAbsolutePath().normalize();
        Path relative;
        if (selected.startsWith(root)) {
            relative = root.relativize(selected);
        } else {
            // File pickers open at the game directory so legacy ldlib/assets and ldlib2/assets
            // content remains selectable while new files default to photon/assets.
            int index = -1;
            for (int i = 0; i < selected.getNameCount(); i++) {
                if (selected.getName(i).toString().equals("assets")) index = i;
            }
            if (index < 0 || index + 2 >= selected.getNameCount()) return null;
            relative = selected.subpath(index + 1, selected.getNameCount());
        }
        if (relative.getNameCount() < 2) return null;
        StringBuilder id = new StringBuilder(relative.getName(0).toString()).append(':');
        for (int i = 1; i < relative.getNameCount(); i++) {
            if (i > 1) id.append('/');
            id.append(relative.getName(i));
        }
        return ResourceLocation.tryParse(id.toString());
    }

    @Nullable
    public static ResourceLocation shaderLocationFromFile(Path assetsRoot, Path file) {
        var asset = fromFile(assetsRoot, file);
        if (asset == null || !asset.getPath().endsWith(".json")) return null;
        String prefix = "shaders/core/";
        int index = asset.getPath().indexOf(prefix);
        if (index < 0) return null;
        String shader = asset.getPath().substring(index + prefix.length());
        if (!shader.endsWith(".json") || shader.length() <= 5) return null;
        return ResourceLocation.tryParse(asset.getNamespace() + ":" + shader.substring(0, shader.length() - 5));
    }

    @Nullable
    public static ResourceLocation modelLocationFromFile(Path assetsRoot, Path file) {
        var asset = fromFile(assetsRoot, file);
        if (asset == null || !asset.getPath().startsWith("models/") || !asset.getPath().endsWith(".json")) return null;
        String model = asset.getPath().substring(7, asset.getPath().length() - 5);
        return model.isEmpty() ? null : ResourceLocation.tryParse(asset.getNamespace() + ":" + model);
    }
}
