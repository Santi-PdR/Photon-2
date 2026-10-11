package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleQuadGeometryTest {
    @Test
    void appliesParticlePositionSizeSpaceScaleAndRotationToEveryCorner() {
        var position = new Vector3f(10, 20, 30);
        var vertices = ParticleQuadGeometry.transformedBillboardVertices(
                2, 3, 1, new Vector3f(2, 1, 1),
                new Quaternionf().rotationZ((float) (Math.PI / 2)), position);

        assertVector(vertices[0], new Vector3f(16, 18, 30));
        assertVector(vertices[1], new Vector3f(4, 18, 30));
        assertVector(vertices[2], new Vector3f(4, 22, 30));
        assertVector(vertices[3], new Vector3f(16, 22, 30));

        var center = new Vector3f();
        for (var vertex : vertices) center.add(vertex);
        center.mul(1f / vertices.length);
        assertVector(center, position);
    }

    private static void assertVector(Vector3f actual, Vector3f expected) {
        assertTrue(actual.distance(expected) < 1e-5f,
                () -> "expected " + expected + " but got " + actual);
    }
}
