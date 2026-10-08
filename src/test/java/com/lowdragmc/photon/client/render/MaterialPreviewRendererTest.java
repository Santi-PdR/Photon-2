package com.lowdragmc.photon.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaterialPreviewRendererTest {
    @Test
    void quantizesPhysicalPreviewSizeToStableSixteenPixelSteps() {
        assertEquals(64, MaterialPreviewRenderer.targetPixels(40, 30, 1.25));
        assertEquals(128, MaterialPreviewRenderer.targetPixels(128, 72, 1));
    }

    @Test
    void clampsPreviewTargetsToSupportedBounds() {
        assertEquals(32, MaterialPreviewRenderer.targetPixels(8, 8, 1));
        assertEquals(512, MaterialPreviewRenderer.targetPixels(2048, 1024, 2));
    }
}
