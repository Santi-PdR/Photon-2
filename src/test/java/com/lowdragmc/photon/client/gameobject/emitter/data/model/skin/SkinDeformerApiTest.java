package com.lowdragmc.photon.client.gameobject.emitter.data.model.skin;

import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SkinDeformerApiTest {
    @Test
    void exposesGeometryOnlyDeformationOverload() throws NoSuchMethodException {
        assertNotNull(SkinDeformer.class.getMethod("deform", PhotonMesh.class, MeshSkin.class, float[].class));
    }
}
