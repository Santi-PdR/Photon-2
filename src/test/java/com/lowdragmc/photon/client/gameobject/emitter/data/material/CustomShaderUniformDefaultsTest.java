package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.google.gson.JsonParser;
import com.lowdragmc.photon.client.render.PhotonCustomUniforms;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

class CustomShaderUniformDefaultsTest {
    @Test
    void readsAuthoredValuesAndZeroFillsTheRequestedWidth() {
        var shader = JsonParser.parseString("""
                {"uniforms":[
                  {"name":"Color","values":[0.25,0.5,0.75,1.0]},
                  {"name":"Radius","values":[0.4]}
                ]}
                """).getAsJsonObject();

        assertArrayEquals(new float[]{0.25f, 0.5f, 0.75f, 1f, 0f},
                CustomShaderUniformDefaults.read(shader, "Color", 5));
        assertArrayEquals(new float[]{0.4f, 0f},
                CustomShaderUniformDefaults.read(shader, "Radius", 2));
        assertArrayEquals(new float[]{0f, 0f},
                CustomShaderUniformDefaults.read(shader, "Missing", 2));
    }

    @Test
    void mapsOnlyCustomUniformsToTheTargetApi() {
        var shader = JsonParser.parseString("""
                {"uniforms":[
                  {"name":"Tint","type":"float","count":4},
                  {"name":"Steps","type":"int"},
                  {"name":"Offset","type":"float","count":2},
                  {"name":"Basis","type":"matrix4x4"},
                  {"name":"ModelViewMat","type":"matrix4x4"},
                  {"name":"U_Internal","type":"float"},
                  {"name":"Unsupported","type":"float","count":5}
                ]}
                """).getAsJsonObject();

        assertEquals(List.of(
                new PhotonCustomUniforms.Field("Tint", PhotonCustomUniforms.Type.VEC4),
                new PhotonCustomUniforms.Field("Steps", PhotonCustomUniforms.Type.INT),
                new PhotonCustomUniforms.Field("Offset", PhotonCustomUniforms.Type.VEC2),
                new PhotonCustomUniforms.Field("Basis", PhotonCustomUniforms.Type.MAT4)),
                CustomShaderUniformDefaults.fields(shader));
    }
}
