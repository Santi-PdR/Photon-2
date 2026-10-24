package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtlasUvRemapTest {
    @Test
    void mapsAtlasCoordinatesIntoSpriteLocalSpace() {
        assertEquals(0.25f, AtlasUvRemap.normalize(0.3f, 0.2f, 0.6f), 1.0e-6f);
        assertEquals(0.75f, AtlasUvRemap.normalize(0.35f, 0.5f, 0.3f), 1.0e-6f);
    }

    @Test
    void preservesCoordinatesWhenSpriteBoundsAreDegenerate() {
        assertEquals(0.42f, AtlasUvRemap.normalize(0.42f, 0.42f, 0.42f));
    }
}
