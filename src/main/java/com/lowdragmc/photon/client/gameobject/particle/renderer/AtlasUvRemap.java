package com.lowdragmc.photon.client.gameobject.particle.renderer;

/** Shared atlas-to-sprite UV normalization for CPU and instanced model rendering. */
final class AtlasUvRemap {
    private AtlasUvRemap() {
    }

    /**
     * Convert one atlas coordinate into its sprite-local coordinate. Degenerate bounds preserve the
     * source coordinate, matching Photon 26.2's renderer and avoiding NaN/Infinity in vertex buffers.
     */
    static float normalize(float coordinate, float min, float max) {
        float span = max - min;
        return span == 0f ? coordinate : (coordinate - min) / span;
    }
}
