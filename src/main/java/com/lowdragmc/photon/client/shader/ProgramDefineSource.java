package com.lowdragmc.photon.client.shader;

import java.util.Collection;

/** Formats LDLib2's bare macro names as valid GLSL preprocessor directives. */
public final class ProgramDefineSource {
    private ProgramDefineSource() {
    }

    public static String format(Collection<String> defines, char delimiter) {
        var source = new StringBuilder();
        for (String define : defines) {
            source.append("#define ").append(define).append(delimiter);
        }
        return source.toString();
    }
}
