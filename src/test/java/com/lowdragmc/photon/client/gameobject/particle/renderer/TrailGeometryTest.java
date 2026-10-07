package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailGeometryTest {
    @Test
    void requiresTwoPointsBeforeEitherRendererBuildsATrail() {
        assertFalse(TrailGeometry.hasSegments(0, true));
        assertFalse(TrailGeometry.hasSegments(1, false));
        assertTrue(TrailGeometry.hasSegments(1, true));
        assertTrue(TrailGeometry.hasSegments(2, false));
    }
}
