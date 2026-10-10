package com.lowdragmc.photon.client.gameobject.emitter.particle;

import org.junit.jupiter.api.Test;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

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
}
