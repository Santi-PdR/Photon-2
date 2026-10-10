package com.lowdragmc.photon.client.shader;

import java.nio.charset.StandardCharsets;

/** Helpers for unique program-cache names that also satisfy ResourceLocation path rules. */
public final class ProgramCacheName {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ProgramCacheName() {}

    /**
     * LDLib2's raw cache suffix contains {@code #}, spaces and newlines. Encode it in lowercase
     * hexadecimal so shader stages remain distinct while mods that parse the program name as a
     * ResourceLocation can safely handle it.
     */
    public static String withDefines(String shaderName, String defineBlock) {
        var bytes = defineBlock.getBytes(StandardCharsets.UTF_8);
        var suffix = new StringBuilder(1 + bytes.length * 2).append('d');
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            suffix.append(HEX[unsigned >>> 4]).append(HEX[unsigned & 0x0f]);
        }
        return shaderName + '_' + suffix;
    }
}
