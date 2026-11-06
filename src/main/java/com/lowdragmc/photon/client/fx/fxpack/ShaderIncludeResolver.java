package com.lowdragmc.photon.client.fx.fxpack;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves vanilla shader imports so fxpack garbage collection retains their dependencies. */
final class ShaderIncludeResolver {
    private static final Pattern IMPORT = Pattern.compile("(?m)^[ \\t]*#moj_import[ \\t]+(?:<([^>]+)>|\"([^\"]+)\")");

    private ShaderIncludeResolver() {
    }

    static List<ResourceLocation> imports(String source, String shaderBasePath) {
        LinkedHashSet<ResourceLocation> locations = new LinkedHashSet<>();
        Matcher matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            boolean includeDirectory = matcher.group(1) != null;
            String name = includeDirectory ? matcher.group(1) : matcher.group(2);
            ResourceLocation location = ResourceLocation.tryParse(name);
            if (location == null) continue;
            // Forge resolves quoted imports relative to the original stage's directory,
            // including quotes found transitively in another imported source.
            String path = normalize((includeDirectory ? "shaders/include/" : shaderBasePath) + location.getPath());
            if (path == null) continue;
            locations.add(new ResourceLocation(location.getNamespace(), path));
        }
        return List.copyOf(new ArrayList<>(locations));
    }

    private static String normalize(String path) {
        if (path.startsWith("/") || path.isBlank()) return null;
        var segments = new ArrayDeque<String>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) continue;
            if (segment.equals("..")) {
                if (segments.isEmpty()) return null;
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        return segments.isEmpty() ? null : String.join("/", segments);
    }
}

