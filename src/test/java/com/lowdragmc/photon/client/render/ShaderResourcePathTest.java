package com.lowdragmc.photon.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        if (id.length != 2) return;
        if (!"photon".equals(id[0])) {
            // Minecraft's built-in stages are supplied by the game. Mod-owned external stages must
            // resolve from their dependency's runtime classpath, so dependency bumps cannot silently
            // leave a packaged Photon program pointing at a missing vertex/fragment resource.
            if (!"minecraft".equals(id[0])) {
                var resource = "assets/" + id[0] + "/shaders/core/" + id[1] + suffix;
                assertNotNull(Thread.currentThread().getContextClassLoader().getResource(resource),
                        () -> jsonPath + " references missing external " + stage + " shader " + id[0] + ":" + id[1]);
            }
            return;
        }
        var resource = CORE.resolve(id[1] + suffix).normalize();
        assertTrue(resource.startsWith(CORE) && Files.isRegularFile(resource),
                () -> jsonPath + " references missing " + stage + " shader " + id[0] + ":" + id[1]);
    }
}
