package com.lowdragmc.photon.client.gameobject.emitter.particle;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleRendererSettingPersistenceTest {
    @Test
    void renderModeIsPersistedSoModelProjectsDoNotRestoreAsBillboards() throws NoSuchFieldException {
        Field renderMode = ParticleRendererSetting.class.getDeclaredField("renderMode");

        assertTrue(renderMode.isAnnotationPresent(Persisted.class));
        assertEquals(ParticleRendererSetting.Mode.Billboard, new ParticleRendererSetting(null).getRenderMode());
    }

    @Test
    void particleConfigRecursivelyPersistsItsRendererSettings() throws NoSuchFieldException {
        Field renderer = ParticleConfig.class.getDeclaredField("renderer");

        assertTrue(renderer.isAnnotationPresent(Persisted.class));
        assertTrue(renderer.getAnnotation(Persisted.class).subPersisted());
    }

    @Test
    void particleConfigPersistsItsVisualEmitterInputs() throws NoSuchFieldException {
        assertPersisted("startSize");
        assertPersisted("startLifetime");
        assertPersisted("emission", true);
        assertPersisted("shape", true);
        assertPersisted("sizeOverLifetime", true);
    }

    @Test
    void gpuInstancingChoiceSurvivesEmitterProjectSerialization() throws NoSuchFieldException {
        Field useGPUInstance = ParticleRendererSetting.class.getDeclaredField("useGPUInstance");

        assertTrue(useGPUInstance.isAnnotationPresent(Persisted.class));
    }

    private static void assertPersisted(String fieldName) throws NoSuchFieldException {
        assertTrue(ParticleConfig.class.getDeclaredField(fieldName).isAnnotationPresent(Persisted.class), fieldName);
    }

    private static void assertPersisted(String fieldName, boolean subPersisted) throws NoSuchFieldException {
        Field field = ParticleConfig.class.getDeclaredField(fieldName);

        assertTrue(field.isAnnotationPresent(Persisted.class), fieldName);
        assertEquals(subPersisted, field.getAnnotation(Persisted.class).subPersisted(), fieldName);
    }

}
