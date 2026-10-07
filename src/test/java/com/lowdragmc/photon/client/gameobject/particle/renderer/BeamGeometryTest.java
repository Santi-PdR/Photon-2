package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeamGeometryTest {
    @Test
    void rejectsZeroLengthBeamsBeforeVertexExpansion() {
        var from = new Vector3f(2, -1, 4);
        assertFalse(BeamGeometry.hasLength(from, new Vector3f(from)));
        assertTrue(BeamGeometry.hasLength(from, new Vector3f(from).add(0, 0, 1e-4f)));
    }

    @Test
    void computesTheExpectedWidthAndKeepsTheSidePerpendicular() {
        var direction = new Vector3f(1, 0, 0);
        var side = BeamGeometry.side(new Vector3f(0, 0, 2), direction, 0.25f);

        assertTrue(Float.isFinite(side.x) && Float.isFinite(side.y) && Float.isFinite(side.z));
        assertEquals(0.25f, side.length(), 1e-6f);
        assertEquals(0f, side.dot(direction), 1e-6f);
    }

    @Test
    void usesAStablePerpendicularWhenTheBeamPointsAtTheCamera() {
        var direction = new Vector3f(0, 0, 0.5f);
        var side = BeamGeometry.side(new Vector3f(0, 0, -3), direction, 0.4f);

        assertTrue(Float.isFinite(side.x) && Float.isFinite(side.y) && Float.isFinite(side.z));
        assertEquals(0.4f, side.length(), 1e-6f);
        assertEquals(0f, side.dot(direction), 1e-6f);
    }

    @Test
    void instancedShaderUsesTheSameCollinearFallback() throws IOException {
        try (var stream = getClass().getResourceAsStream("/assets/photon/shaders/include/particle.glsl")) {
            assertNotNull(stream);
            var shader = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(shader.contains("vec3 beamSide = cross(iStart.xyz, beamDir);"));
            assertTrue(shader.contains("abs(beamDir.z) < 0.9 * length(beamDir)"));
            assertTrue(shader.contains("beamSide = cross(fallbackAxis, beamDir);"));
        }
    }
}
