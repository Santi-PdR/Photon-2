package com.lowdragmc.photon.client.render;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhotonDepthParamsTest {
    @Test
    void mapsForwardZWindowDepthAndExtractsPerspectiveRange() {
        var params = PhotonDepthParams.fromProjection(new Matrix4f().perspective(
                (float) Math.toRadians(70), 16f / 9f, 0.1f, 256f));

        assertEquals(2f, params.x, 0f);
        assertEquals(-1f, params.y, 0f);
        assertEquals(0.1f, params.z, 1e-4f);
        assertEquals(256f, params.w, 0.1f);
    }

    @Test
    void extractsOrthographicDepthRange() {
        var params = PhotonDepthParams.fromProjection(new Matrix4f().ortho(-2f, 2f, -1f, 1f, 0.5f, 80f));

        assertEquals(0.5f, params.z, 1e-5f);
        assertEquals(80f, params.w, 1e-4f);
    }
}
