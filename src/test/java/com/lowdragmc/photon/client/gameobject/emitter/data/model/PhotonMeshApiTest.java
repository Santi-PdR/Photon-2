package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
