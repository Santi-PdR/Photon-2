package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DynamicMeshCacheTest {
    @Test
    void lazyTangentsStayConsistentWithTheGeometryRevision() {
        var topology = new PhotonMesh.Builder()
                .triangle(vertex(0, 0), vertex(1, 0), vertex(0, 1))
                .build();
        var dynamic = new MutableDynamicMesh(topology);
        float[] firstGeometry = rotateGeometry(topology.geometry());
        dynamic.update(1, firstGeometry, tangents(0, 1, 0));
        var cache = new DynamicMeshCache();

        PhotonMesh firstPose = cache.resolve(dynamic);
        assertSame(firstPose, cache.resolve(dynamic));

        dynamic.update(2, topology.geometry(), tangents(0, 0, 1));
        float[] expectedFirstPoseTangents = topology
                .withGeometry(firstGeometry, (java.util.function.Supplier<float[]>) null, 1)
                .tangents();
        assertArrayEquals(expectedFirstPoseTangents, firstPose.tangents());

        PhotonMesh secondPose = cache.resolve(dynamic);
        assertArrayEquals(dynamic.tangents(), secondPose.tangents());
    }

    private static float[] vertex(float x, float y) {
        return new float[]{x, y, 0, x, y, 0, 0, 1};
    }

    private static float[] tangents(float x, float y, float z) {
        float[] result = new float[4 * PhotonMesh.FLOATS_PER_TANGENT];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * PhotonMesh.FLOATS_PER_TANGENT;
            result[offset] = x;
            result[offset + 1] = y;
            result[offset + 2] = z;
            result[offset + 3] = 1;
        }
        return result;
    }

    private static float[] rotateGeometry(float[] geometry) {
        float[] result = geometry.clone();
        for (int vertex = 0; vertex < result.length / PhotonMesh.FLOATS_PER_GEOMETRY; vertex++) {
            int offset = vertex * PhotonMesh.FLOATS_PER_GEOMETRY;
            float x = result[offset];
            result[offset] = -result[offset + 1];
            result[offset + 1] = x;
        }
        return result;
    }

    private static final class MutableDynamicMesh implements IDynamicMesh {
        private final PhotonMesh topology;
        private long revision;
        private float[] geometry;
        private float[] tangents;

        private MutableDynamicMesh(PhotonMesh topology) {
            this.topology = topology;
        }

        synchronized void update(long revision, float[] geometry, float[] tangents) {
            this.revision = revision;
            this.geometry = geometry;
            this.tangents = tangents;
        }

        @Override public PhotonMesh topology() { return topology; }
        @Override public synchronized long revision() { return revision; }
        @Override public synchronized float[] geometry() { return geometry; }
        @Override public synchronized float[] tangents() { return tangents; }
    }
}
