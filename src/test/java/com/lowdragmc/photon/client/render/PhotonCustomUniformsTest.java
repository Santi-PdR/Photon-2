package com.lowdragmc.photon.client.render;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhotonCustomUniformsTest {
    @Test
    void parsesReferenceUniformTypeNamesCaseInsensitively() {
        assertEquals(PhotonCustomUniforms.Type.FLOAT, PhotonCustomUniforms.Type.parse("float"));
        assertEquals(PhotonCustomUniforms.Type.INT, PhotonCustomUniforms.Type.parse("INT"));
        assertEquals(PhotonCustomUniforms.Type.VEC2, PhotonCustomUniforms.Type.parse("vec2"));
        assertEquals(PhotonCustomUniforms.Type.VEC3, PhotonCustomUniforms.Type.parse("VEC3"));
        assertEquals(PhotonCustomUniforms.Type.VEC4, PhotonCustomUniforms.Type.parse("vec4"));
        assertEquals(PhotonCustomUniforms.Type.MAT4, PhotonCustomUniforms.Type.parse("mat4"));
        assertNull(PhotonCustomUniforms.Type.parse("matrix4x4"));
        assertNull(PhotonCustomUniforms.Type.parse(null));
    }

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
