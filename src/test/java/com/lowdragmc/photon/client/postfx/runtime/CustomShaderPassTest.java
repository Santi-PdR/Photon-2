package com.lowdragmc.photon.client.postfx.runtime;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void everyBuiltinPassChoiceHasAPackagedShaderDefinition() throws IOException {
        assertEquals(CustomShaderPass.BUILTIN_SHADERS.size(),
                new HashSet<>(CustomShaderPass.BUILTIN_SHADERS).size(), "built-in shader choices must be unique");
        for (var shader : CustomShaderPass.BUILTIN_SHADERS) {
            var id = shader.split(":", 2);
            assertEquals(2, id.length, "shader choice must be namespaced: " + shader);
            var jsonPath = "/assets/" + id[0] + "/shaders/core/" + id[1] + ".json";
            try (var stream = getClass().getResourceAsStream(jsonPath)) {
                assertNotNull(stream, "built-in pass choice has no packaged shader definition: " + shader);
            }
        }
    }
}
