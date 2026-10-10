package com.lowdragmc.photon.client.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramCacheNameTest {
    @Test
    void encodesLdLibDefineBlockAsResourceLocationSafeSuffix() {
        var name = ProgramCacheName.withDefines("ldlib2:tornado_body", "#define LD_SHADER_0_\n");

        assertEquals("ldlib2:tornado_body_d23646566696e65204c445f5348414445525f305f0a", name);
        assertTrue(name.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"));
    }

    @Test
    void keepsDifferentDefineVariantsInDifferentProgramCacheEntries() {
        assertNotEquals(
                ProgramCacheName.withDefines("photon:particle", "#define LD_SHADER_0_\n"),
                ProgramCacheName.withDefines("photon:particle", "#define LD_SHADER_1_\n"));
    }
}
