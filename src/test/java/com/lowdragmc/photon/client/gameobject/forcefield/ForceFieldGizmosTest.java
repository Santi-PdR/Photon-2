package com.lowdragmc.photon.client.gameobject.forcefield;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForceFieldGizmosTest {
    @Test
    void tinyPositiveRadiusCanProduceOnlySegmentsDiscardedByTheLineRenderer() {
        var edges = ForceFieldGizmos.getGuideLines(ForceFieldConfig.Shape.Sphere, 1.0e-8f);

        assertFalse(edges.isEmpty());
        assertTrue(edges.stream().allMatch(edge -> edge.getA().distanceSquared(edge.getB()) < 1.0e-12f));
    }
}
