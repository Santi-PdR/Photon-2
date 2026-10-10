package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextureMaterialPathTest {
    private static final Path ASSETS = Path.of("/profile/ldlib2/assets");

    @Test
    void mapsFilesInTheDirectAssetsNamespace() {
        var file = ASSETS.resolve("photon/textures/sparky.png");

        assertEquals(ResourceLocation.parse("photon:textures/sparky.png"),
                TextureAssetPath.fromFile(ASSETS, file));
    }

    @Test
    void mapsFilesFromLDLib2sNestedResourcePack() {
        var file = ASSETS.resolve("ldlib2/assets/photon/textures/sparky.png");

        assertEquals(ResourceLocation.parse("photon:textures/sparky.png"),
                TextureAssetPath.fromFile(ASSETS, file));
    }

    @Test
    void mapsFilesFromTheNestedLDLib2Namespace() {
        var file = ASSETS.resolve("ldlib2/assets/ldlib2/textures/dall.png");

        assertEquals(ResourceLocation.parse("ldlib2:textures/dall.png"),
                TextureAssetPath.fromFile(ASSETS, file));
    }

    @Test
    void rejectsFilesOutsideTheAssetsDirectory() {
        var file = Path.of("/profile/other/assets/photon/textures/sparky.png");

        assertNull(TextureAssetPath.fromFile(ASSETS, file));
    }
}
