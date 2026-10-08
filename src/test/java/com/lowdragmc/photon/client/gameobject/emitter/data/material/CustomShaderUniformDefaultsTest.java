package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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
}
