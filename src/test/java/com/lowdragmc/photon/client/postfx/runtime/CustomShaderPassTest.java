package com.lowdragmc.photon.client.postfx.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                var info = CustomShaderPass.parse(json);
                assertEquals(jsonNames(json, "samplers"), info.samplers(), "sampler ports drifted for " + shader);
                assertEquals(expectedValuePorts(json), info.uniforms().stream()
                        .map(CustomShaderPass.UniformSpec::name).toList(), "value ports drifted for " + shader);
            }
        }
    }

    private static List<String> jsonNames(JsonObject json, String arrayName) {
        if (!json.has(arrayName)) return List.of();
        return json.getAsJsonArray(arrayName).asList().stream()
                .map(element -> element.getAsJsonObject().get("name").getAsString()).toList();
    }

    private static List<String> expectedValuePorts(JsonObject json) {
        if (!json.has("uniforms")) return List.of();
        var executorUniforms = Set.of("ScreenSize", "GameTime", "ProjMat", "ModelViewMat", "ZNear", "ZFar",
                "U_ViewPort", "U_InverseProjectionMatrix", "U_InverseViewMatrix", "U_CameraPosition",
                "U_DepthParams", "kg_CameraBlockPos", "kg_CameraOffset", "kg_Time", "kg_ViewMat",
                "kg_IViewMat", "kg_IModelViewMat", "kg_IProjMat");
        return json.getAsJsonArray("uniforms").asList().stream()
                .map(element -> element.getAsJsonObject())
                .filter(uniform -> "float".equals(uniform.has("type")
                        ? uniform.get("type").getAsString() : "float"))
                .filter(uniform -> {
                    var name = uniform.get("name").getAsString();
                    int count = uniform.has("count") ? uniform.get("count").getAsInt() : 1;
                    return count >= 1 && count <= 4 && !executorUniforms.contains(name) && !name.endsWith("_TexelSize");
                })
                .map(uniform -> uniform.get("name").getAsString()).toList();
    }
}
