package com.lowdragmc.photon.client.fx.fxpack;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves Minecraft 1.20.1 {@code #moj_import} directives to resource-manager locations. */
final class ShaderIncludeResolver {
    private static final Pattern IMPORT = Pattern.compile(
            "(?m)^[ \\t]*#moj_import[ \\t]+(?:<([^>]+)>|\"([^\"]+)\")");

    private ShaderIncludeResolver() {
    }

    static List<ResourceLocation> imports(String source) {
        var locations = new LinkedHashSet<ResourceLocation>();
        Matcher matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            boolean includeDirectory = matcher.group(1) != null;
            String name = includeDirectory ? matcher.group(1) : matcher.group(2);
            var location = parseName(name);
            if (location == null) continue;

            String path = includeDirectory ? "shaders/include/" + location.getPath() : location.getPath();
            if (hasParentSegment(path)) continue;
            locations.add(ResourceLocation.fromNamespaceAndPath(location.getNamespace(), path));
        }
        return List.copyOf(new ArrayList<>(locations));
    }

    private static ResourceLocation parseName(String name) {
        return name.indexOf(':') >= 0
                ? ResourceLocation.tryParse(name)
                : ResourceLocation.tryBuild("minecraft", name);
    }

    private static boolean hasParentSegment(String path) {
        for (var segment : path.split("/")) {
            if (segment.equals("..")) return true;
        }
        return path.startsWith("/") || path.isBlank();
    }
}
