package com.lowdragmc.photon.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderResourcePathTest {
    private static final Path CORE = Path.of("src/main/resources/assets/photon/shaders/core");

    @Test
    void everyPackagedPhotonCoreShaderJsonResolvesItsOwnStages() throws IOException {
        assertTrue(Files.isDirectory(CORE), "Photon core shader resources must be present");
        try (var paths = Files.walk(CORE)) {
            for (var jsonPath : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
                try (var reader = Files.newBufferedReader(jsonPath)) {
                    var shader = JsonParser.parseReader(reader).getAsJsonObject();
                    assertStageExists(shader, "vertex", ".vsh", jsonPath);
                    assertStageExists(shader, "fragment", ".fsh", jsonPath);
                }
            }
        }
    }

    private static void assertStageExists(JsonObject shader, String stage, String suffix, Path jsonPath) {
        if (!shader.has(stage)) return;
        var id = shader.get(stage).getAsString().split(":", 2);
        if (id.length != 2 || !"photon".equals(id[0])) return; // Minecraft/LDLib2 owns the external stages.
        var resource = CORE.resolve(id[1] + suffix).normalize();
        assertTrue(resource.startsWith(CORE) && Files.isRegularFile(resource),
                () -> jsonPath + " references missing " + stage + " shader " + id[0] + ":" + id[1]);
    }
}
