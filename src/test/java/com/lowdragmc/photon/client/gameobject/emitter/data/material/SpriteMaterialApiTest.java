package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SpriteMaterialApiTest {
    @Test
    void exposesSoftParticleConfigurationToJavaIntegrations() throws NoSuchMethodException {
        assertNotNull(SpriteMaterial.class.getMethod("getSoftParticles"));
    }
}
