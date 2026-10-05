package com.lowdragmc.photon.client.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineShaderIncludeTest {
    @Test
    void packagesForwardZCameraAndDepthShaderContract() throws IOException {
        try (var stream = getClass().getResourceAsStream("/assets/photon/shaders/include/engine.glsl")) {
            assertNotNull(stream, "engine.glsl must be available to shaders at runtime");
            var source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(source.contains("uniform mat4 U_InverseProjectionMatrix;"));
            assertTrue(source.contains("uniform vec4 U_CameraPosition;"));
            assertTrue(source.contains("uniform vec4 U_ViewPort;"));
            assertTrue(source.contains("uniform vec4 U_DepthParams;"));
            assertTrue(source.contains("float photon_ndc_depth(float depth)"));
            assertTrue(source.contains("float photon_eye_depth(float depth)"));
        }
    }
}
