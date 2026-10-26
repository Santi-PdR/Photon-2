package com.lowdragmc.photon.client.fx.compat;

import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Adapts newer custom shader calls in memory, preserving editable shader files on disk. */
public final class ForgeShaderCompat {
    private static final Pattern FOG_CALL = Pattern.compile("\\bfog_distance\\s*\\(");

    private ForgeShaderCompat() {}

    public static ResourceProvider wrap(ResourceProvider provider) {
        return location -> provider.getResource(location).map(resource -> {
            if (!location.getPath().endsWith(".vsh")) return resource;
            return new Resource(resource.source(), () -> {
                try (var stream = resource.open()) {
                    String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    return new ByteArrayInputStream(convertVertex(source).getBytes(StandardCharsets.UTF_8));
                }
            });
        });
    }

    /** 1.21 takes (position, shape); 1.20.1 additionally requires the model-view matrix. */
    public static String convertVertex(String source) {
        var matches = FOG_CALL.matcher(source);
        var result = new StringBuilder(source.length());
        int copied = 0;
        while (matches.find()) {
            int open = matches.end() - 1;
            int depth = 1;
            int commas = 0;
            int close = open + 1;
            for (; close < source.length() && depth != 0; close++) {
                char character = source.charAt(close);
                if (character == '(') depth++;
                else if (character == ')') depth--;
                else if (character == ',' && depth == 1) commas++;
            }
            if (depth == 0 && commas == 1) {
                result.append(source, copied, open + 1).append("ModelViewMat, ");
                copied = open + 1;
            }
        }
        return result.append(source, copied, source.length()).toString();
    }
}

