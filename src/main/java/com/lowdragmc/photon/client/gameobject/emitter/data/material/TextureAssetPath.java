package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import java.nio.file.Path;

/** Resolves an image selected from LDLib2's asset browser to its resource-pack location. */
final class TextureAssetPath {
    private TextureAssetPath() {
    }

    static net.minecraft.resources.ResourceLocation fromFile(Path assetsDirectory, Path filePath) {
        return com.lowdragmc.photon.client.util.PhotonAssetPath.fromFile(assetsDirectory, filePath);
    }
}
