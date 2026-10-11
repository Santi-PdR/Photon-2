package com.lowdragmc.photon.client.util;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhotonAssetPathTest {
    private static final Path ASSETS = Path.of("/profile/ldlib2/assets");

    @Test
    void mapsFilesInTheDirectAssetsNamespace() {
        assertEquals(ResourceLocation.parse("photon:shaders/core/spark.json"),
                PhotonAssetPath.fromFile(ASSETS, ASSETS.resolve("photon/shaders/core/spark.json")));
    }

    @Test
    void mapsFilesInLDLib2sNestedAssetPack() {
        assertEquals(ResourceLocation.parse("ldlib2:shaders/core/spark.json"),
                PhotonAssetPath.fromFile(ASSETS,
                        ASSETS.resolve("ldlib2/assets/ldlib2/shaders/core/spark.json")));
    }

    @Test
    void rejectsFilesOutsideTheAssetsDirectory() {
        assertNull(PhotonAssetPath.fromFile(ASSETS,
                Path.of("/profile/other/assets/photon/shaders/core/spark.json")));
    }

    @Test
    void mapsNestedShaderSelectionsToShaderResourceLocations() {
        assertEquals(ResourceLocation.parse("ldlib2:effects/tornado"),
                PhotonAssetPath.shaderLocationFromFile(ASSETS,
                        ASSETS.resolve("ldlib2/assets/ldlib2/shaders/core/effects/tornado.json")));
    }

    @Test
    void rejectsNonShaderFilesWhenSelectingAShader() {
        assertNull(PhotonAssetPath.shaderLocationFromFile(ASSETS,
                ASSETS.resolve("ldlib2/assets/ldlib2/textures/particle/smoke.json")));
    }
}
