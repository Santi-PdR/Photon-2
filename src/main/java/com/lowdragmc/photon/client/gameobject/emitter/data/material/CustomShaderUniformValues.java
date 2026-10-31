package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import java.util.Arrays;

final class CustomShaderUniformValues {
    private CustomShaderUniformValues() {
    }

    static int[] toIntegerComponents(float[] components) {
        int[] integers = new int[components.length];
        for (int i = 0; i < components.length; i++) integers[i] = (int) components[i];
        return integers;
    }

    static float[] fitComponents(float[] components, int count) {
        if (components == null || count <= 0 || count > 16) return new float[0];
        return Arrays.copyOf(components, count);
    }
}
