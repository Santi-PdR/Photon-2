package com.lowdragmc.photon.client.postfx.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledEffectDepthTest {
    @Test
    void detectsWhenACompiledPassReadsSceneDepth() {
        var depthPass = new CompiledEffect.CompiledPass(null, null,
                Map.of("SamplerSceneDepth", CompiledEffect.ResourceRef.SCENE_DEPTH_REF), Map.of(), 0);
        var colorPass = new CompiledEffect.CompiledPass(null, null,
                Map.of("Input", CompiledEffect.ResourceRef.SCENE_COLOR_REF), Map.of(), 0);

        assertTrue(effect(depthPass).usesSceneDepth());
        assertFalse(effect(colorPass).usesSceneDepth());
    }

    private static CompiledEffect effect(CompiledEffect.CompiledPass pass) {
        return new CompiledEffect(null, 0, true, List.of(), List.of(), List.of(pass), 0);
    }
}
