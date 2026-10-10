package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/** Resolves an image selected from LDLib2's asset browser to its resource-pack location. */
final class TextureAssetPath {
    private TextureAssetPath() {
    }

    @Nullable
    static ResourceLocation fromFile(Path assetsDirectory, Path filePath) {
        var assetRoot = assetsDirectory.toAbsolutePath().normalize();
        var selected = filePath.toAbsolutePath().normalize();
        if (!selected.startsWith(assetRoot)) return null;

        var relative = assetRoot.relativize(selected);
        if (relative.getNameCount() < 2) return null;

        // LDLib2 stores some editor assets under <assets>/ldlib2/assets/<namespace>/....
        // The directory returned by getAssetsDir() is the first <assets> in that path, so blindly
        // using indexOf("assets/") treated "assets/ldlib2" as part of the resource path.
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
}
