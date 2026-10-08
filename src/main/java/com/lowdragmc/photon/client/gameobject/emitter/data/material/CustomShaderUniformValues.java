package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import java.util.Arrays;

/** Value conversions shared with Photon 26.2's custom-uniform buffer contract. */
final class CustomShaderUniformValues {
    private CustomShaderUniformValues() {
    }

    /** Photon 26.2 writes float-backed integer values with JVM {@code f2i} semantics (truncate toward zero). */
    static int[] toIntegerComponents(float[] components) {
        var integers = new int[components.length];
        for (int i = 0; i < components.length; i++) {
            integers[i] = (int) components[i];
        }
        return integers;
    }

    /** Fits values to the declared uniform width, zero-filling short values like Photon 26.2. */
    static float[] fitComponents(float[] components, int count) {
        if (components == null || count <= 0 || count > 16) return new float[0];
        return Arrays.copyOf(components, count);
    }
}
