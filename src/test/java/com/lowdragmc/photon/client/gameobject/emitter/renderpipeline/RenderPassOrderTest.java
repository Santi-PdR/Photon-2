package com.lowdragmc.photon.client.gameobject.emitter.renderpipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderPassOrderTest {
    @Test
    void comparesLayerOrdersWithoutIntegerOverflow() {
        assertTrue(RenderPassPipeline.compareRenderPassOrder(
                Integer.MIN_VALUE, 0, 1L, Integer.MAX_VALUE, 0, 2L) < 0);
        assertTrue(RenderPassPipeline.compareRenderPassOrder(
                Integer.MAX_VALUE, 0, 1L, Integer.MIN_VALUE, 0, 2L) > 0);
    }

    @Test
    void preservesUnequalPassesWithCollidingHashes() {
        assertTrue(RenderPassPipeline.compareRenderPassOrder(7, 42, 1L, 7, 42, 2L) < 0);
        assertTrue(RenderPassPipeline.compareRenderPassOrder(7, 42, 2L, 7, 42, 1L) > 0);
    }
}
