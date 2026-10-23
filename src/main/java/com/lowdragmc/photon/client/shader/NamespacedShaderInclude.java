package com.lowdragmc.photon.client.shader;

import net.minecraft.resources.ResourceLocation;

import java.util.function.UnaryOperator;

/** Compatibility for namespaced GLSL angle imports used by Photon 2 shaders. */
public final class NamespacedShaderInclude {
    private static final String VANILLA_INCLUDE_DIRECTORY = "shaders/include/";

    private NamespacedShaderInclude() {
    }

    /**
     * Converts the path produced by Minecraft 1.20.1's ShaderInstance preprocessor into a resource
     * location. Its angle-import resolver prefixes {@code shaders/include/} before parsing, while
     * newer Photon shader sources can express the namespace before the include path.
     */
    public static String normalize(String preprocessedPath, UnaryOperator<String> vanillaNormalizer) {
        int colon = preprocessedPath.indexOf(':');
        if (colon > VANILLA_INCLUDE_DIRECTORY.length()
                && preprocessedPath.startsWith(VANILLA_INCLUDE_DIRECTORY)) {
            String namespace = preprocessedPath.substring(VANILLA_INCLUDE_DIRECTORY.length(), colon);
            String includePath = preprocessedPath.substring(colon + 1);
            if (!includePath.isEmpty()) {
                return namespace + ":" + vanillaNormalizer.apply(VANILLA_INCLUDE_DIRECTORY + includePath);
            }
        }
        return vanillaNormalizer.apply(preprocessedPath);
    }
}
