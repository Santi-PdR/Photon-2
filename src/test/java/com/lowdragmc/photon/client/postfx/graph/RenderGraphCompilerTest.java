package com.lowdragmc.photon.client.postfx.graph;

import com.lowdragmc.photon.client.postfx.runtime.CompiledEffect.ResourceRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderGraphCompilerTest {
    @Test
    void transientResourcesLiveThroughTheirLastDownstreamConsumer() {
        var lastUses = RenderGraphCompiler.calculateLastUses(List.of(
                Map.of(),
                Map.of("Input", ResourceRef.of(0)),
                Map.of("Input", ResourceRef.of(0), "Depth", ResourceRef.SCENE_DEPTH_REF,
                        "Aux", ResourceRef.of(2)),
                Map.of("Input", ResourceRef.of(1), "Scene", ResourceRef.SCENE_COLOR_REF)));

        assertEquals(2, lastUses[0]);
        assertEquals(3, lastUses[1]);
        assertEquals(2, lastUses[2]);
    }
}
