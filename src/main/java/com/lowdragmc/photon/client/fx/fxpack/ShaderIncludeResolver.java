package com.lowdragmc.photon.client.fx.fxpack;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves vanilla shader imports so fxpack garbage collection retains their dependencies. */
final class ShaderIncludeResolver {
    private static final Pattern IMPORT = Pattern.compile("(?m)^[ \\t]*#moj_import[ \\t]+(?:<([^>]+)>|\"([^\"]+)\")");

    private ShaderIncludeResolver() {
    }

    static List<ResourceLocation> imports(String source) {
        LinkedHashSet<ResourceLocation> locations = new LinkedHashSet<>();
        Matcher matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            boolean includeDirectory = matcher.group(1) != null;
            String name = includeDirectory ? matcher.group(1) : matcher.group(2);
            ResourceLocation location = name.indexOf(':') >= 0
                    ? ResourceLocation.tryParse(name)
                    : new ResourceLocation("minecraft", name);
            if (location == null) continue;
            String path = includeDirectory ? "shaders/include/" + location.getPath() : location.getPath();
            if (hasParentSegment(path)) continue;
            locations.add(new ResourceLocation(location.getNamespace(), path));
        }
        return List.copyOf(new ArrayList<>(locations));
    }

    private static boolean hasParentSegment(String path) {
        for (String segment : path.split("/")) {
            if (segment.equals("..")) return true;
        }
        return path.startsWith("/") || path.isBlank();
    }
}

