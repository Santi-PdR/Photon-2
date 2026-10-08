package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CustomShaderUniformValuesTest {
    @Test
    void integerUniformComponentsTruncateTowardZeroLikeReferenceBufferUpload() {
        assertArrayEquals(new int[]{1, -1, 0, 0, 2, -2},
                CustomShaderUniformValues.toIntegerComponents(
                        new float[]{1.6f, -1.6f, 0.9f, -0.9f, 2f, -2f}));
    }
}
