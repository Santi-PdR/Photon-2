package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** CPU billboard quad construction shared by the particle render path and geometry tests. */
final class ParticleQuadGeometry {
    private static final Vector3f[] CORNERS = {
            new Vector3f(-1, -1, 0),
            new Vector3f(-1, 1, 0),
            new Vector3f(1, 1, 0),
            new Vector3f(1, -1, 0)
    };

    private ParticleQuadGeometry() {
    }

    static Vector3f[] transformedBillboardVertices(float sizeX, float sizeY, float sizeZ,
                                                    Vector3f spaceScale, Quaternionf rotation,
                                                    Vector3f position) {
        var vertices = new Vector3f[CORNERS.length];
        for (int i = 0; i < CORNERS.length; i++) {
            var local = new Vector3f(CORNERS[i]).mul(sizeX, sizeY, sizeZ);
            vertices[i] = rotation.transform(local).mul(spaceScale).add(position);
        }
        return vertices;
    }
}
