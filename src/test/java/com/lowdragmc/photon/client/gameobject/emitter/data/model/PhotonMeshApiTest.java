package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PhotonMeshApiTest {
    @Test
    void exposesPerVertexTangentOffsetAndKeepsCornerOffset() throws NoSuchMethodException {
        assertNotNull(PhotonMesh.class.getMethod("tangentOffset", int.class));
        assertEquals(0, PhotonMesh.tangentOffset(0));
        assertEquals(PhotonMesh.FLOATS_PER_TANGENT, PhotonMesh.tangentOffset(1));
        assertEquals(3 * PhotonMesh.FLOATS_PER_TANGENT, PhotonMesh.tangentOffset(3));

        assertNotNull(PhotonMesh.class.getMethod("tangentOffset", int.class, int.class));
        assertEquals(PhotonMesh.tangentOffset(3), PhotonMesh.tangentOffset(0, 3));
    }

    @Test
    void translatingJsonObjMeshMatchesBakedJsonCenteringWithoutChangingSourceMesh() {
        var source = new PhotonMesh.Builder().quad(
                vertex(0.5f, 0f, 1f, 0f, 0f),
                vertex(1.5f, 0f, 1f, 1f, 0f),
                vertex(1.5f, 2f, 1f, 1f, 1f),
                vertex(0.5f, 2f, 1f, 0f, 1f),
                0.8f).build();

        var centered = source.translated(-0.5f, -0.5f, -0.5f);

        assertSame(source.topology(), centered.topology());
        assertEquals(1L, centered.geometryRevision());
        assertArrayEquals(new float[]{0f, -0.5f, 0.5f, 1f, -0.5f, 0.5f,
                        1f, 1.5f, 0.5f, 0f, 1.5f, 0.5f},
                positions(centered), 1.0e-6f);
        assertArrayEquals(new float[]{0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f},
                uv(centered), 1.0e-6f);
        assertArrayEquals(new float[]{0.5f, 0f, 1f, 1.5f, 0f, 1f,
                        1.5f, 2f, 1f, 0.5f, 2f, 1f},
                positions(source), 1.0e-6f);
    }

    private static float[] vertex(float x, float y, float z, float u, float v) {
        return new float[]{x, y, z, u, v, 0f, 0f, 1f};
    }

    private static float[] positions(PhotonMesh mesh) {
        float[] geometry = mesh.geometry();
        float[] result = new float[mesh.vertexCount() * 3];
        for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
            System.arraycopy(geometry, vertex * PhotonMesh.FLOATS_PER_GEOMETRY,
                    result, vertex * 3, 3);
        }
        return result;
    }

    private static float[] uv(PhotonMesh mesh) {
        float[] attributes = mesh.attributes();
        float[] result = new float[mesh.vertexCount() * 2];
        for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
            System.arraycopy(attributes, vertex * PhotonMesh.FLOATS_PER_ATTRIBUTE,
                    result, vertex * 2, 2);
        }
        return result;
    }
}
