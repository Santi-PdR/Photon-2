package com.lowdragmc.photon.client.postfx.runtime;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomShaderPassTest {
    @Test
    void cameraAndDepthUniformsAreEngineManagedRatherThanPassPorts() {
        var info = CustomShaderPass.parse(JsonParser.parseString("""
                {
                  "uniforms": [
                    {"name":"U_CameraPosition", "type":"float", "count":4, "values":[0,0,0,1]},
                    {"name":"U_DepthParams", "type":"float", "count":4, "values":[2,-1,0.05,1000]},
                    {"name":"UserTint", "type":"float", "count":4, "values":[1,1,1,1]},
                    {"name":"U_InverseProjectionMatrix", "type":"matrix4x4", "count":16}
                  ]
                }
                """).getAsJsonObject());

        assertEquals(1, info.uniforms().size());
        assertEquals("UserTint", info.uniforms().get(0).name());
        assertTrue(info.samplers().isEmpty());
    }
}
