package com.lowdragmc.photon.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderResourceReferencesTest {
    private static final String SHADER_ROOT = "assets/photon/shaders/core";
    private static final Pattern IMPORT = Pattern.compile("#moj_import\\s*[<\\\"]([^>\\\"]+)[>\\\"]");

    @Test
    void shaderProgramsAndImportsResolveOnTheRuntimeClasspath() throws Exception {
        var loader = ShaderResourceReferencesTest.class.getClassLoader();
        URL rootUrl = Objects.requireNonNull(loader.getResource(SHADER_ROOT), "Photon shader assets");
        Path root = Path.of(rootUrl.toURI());
        var failures = new ArrayList<String>();
        var checked = new int[2];
        var visitedImports = new HashSet<String>();

        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                if (name.endsWith(".json")) {
                    checkProgram(path, loader, failures, checked);
                } else if (name.endsWith(".vsh") || name.endsWith(".fsh")) {
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    checkImports(SHADER_ROOT + "/" + relative, loader, failures, checked, visitedImports);
                }
            }
        }

        assertTrue(checked[0] > 0, "No Photon shader program descriptors were checked");
        assertTrue(checked[1] > 0, "No Photon shader imports were checked");
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }

    private static void checkProgram(Path descriptor, ClassLoader loader, List<String> failures, int[] checked)
            throws Exception {
        JsonObject json = JsonParser.parseString(Files.readString(descriptor)).getAsJsonObject();
        if (!json.has("vertex") || !json.has("fragment")) return;
        checked[0]++;

        for (var stage : List.of(new Stage("vertex", ".vsh"), new Stage("fragment", ".fsh"))) {
            String identifier = json.get(stage.name()).getAsString();
            String[] parts = identifier.split(":", 2);
            String namespace = parts.length == 2 ? parts[0] : "minecraft";
            String path = parts.length == 2 ? parts[1] : parts[0];
            String resource = "assets/" + namespace + "/shaders/core/" + path + stage.extension();
            if (loader.getResource(resource) == null) {
                failures.add(descriptor + " references missing " + stage.name() + " source " + resource);
            }
        }
    }

    private static void checkImports(String firstResource, ClassLoader loader, List<String> failures, int[] checked,
                                     Set<String> visited)
            throws Exception {
        var pending = new ArrayDeque<String>();
        pending.add(firstResource);
        while (!pending.isEmpty()) {
            String shader = pending.removeFirst();
            if (!visited.add(shader)) continue;
            URL url = loader.getResource(shader);
            if (url == null) {
                failures.add("missing shader source " + shader);
                continue;
            }
            String source;
            try (var input = url.openStream()) {
                source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            Matcher matcher = IMPORT.matcher(source);
            while (matcher.find()) {
                checked[1]++;
                String identifier = matcher.group(1);
                String[] parts = identifier.split(":", 2);
                String namespace = parts.length == 2 ? parts[0] : "minecraft";
                String path = parts.length == 2 ? parts[1] : parts[0];
                pending.addLast("assets/" + namespace + "/shaders/include/" + path);
            }
        }
    }

    private record Stage(String name, String extension) {
    }
}
