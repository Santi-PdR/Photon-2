package com.lowdragmc.photon.client.render;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PhotonCustomUniformsTest {
    @Test
    void stagesClonedValuesAndPreparesTheOwningMaterial() {
        var stagedName = new AtomicReference<String>();
        var stagedValues = new AtomicReference<float[]>();
        var prepareCalls = new AtomicInteger();
        var uniforms = new PhotonCustomUniforms(
                List.of(new PhotonCustomUniforms.Field("Tint", PhotonCustomUniforms.Type.VEC3)),
                (name, values) -> {
                    stagedName.set(name);
                    stagedValues.set(values);
                }, prepareCalls::incrementAndGet);
        float[] values = {0.2f, 0.4f, 0.6f};

        uniforms.set("Tint", values);
        values[0] = 1f;
        uniforms.prepareUpload();

        assertEquals("Tint", stagedName.get());
        assertArrayEquals(new float[]{0.2f, 0.4f, 0.6f}, stagedValues.get());
        assertEquals(1, prepareCalls.get());
        assertFalse(uniforms.isEmpty());
    }
}
