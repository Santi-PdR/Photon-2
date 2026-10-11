package com.lowdragmc.photon.client.shader;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgramDefineSourceTest {
    @Test
    void separatesGlslDefineDirectiveFromMacroName() {
        assertEquals("#define PARTICLE_MODEL_INSTANCE\n#define PHOTON_TANGENT\n",
                ProgramDefineSource.format(List.of("PARTICLE_MODEL_INSTANCE", "PHOTON_TANGENT"), '\n'));
    }

    @Test
    void preservesTheRequestedDelimiterForProgramCacheNames() {
        assertEquals("#define PARTICLE_INSTANCE_",
                ProgramDefineSource.format(List.of("PARTICLE_INSTANCE"), '_'));
    }
}
