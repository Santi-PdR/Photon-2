package com.lowdragmc.photon.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void sceneCaptureFreshnessHasABoundedAge() {
        assertTrue(PreviewSceneCapturePolicy.isFresh(1_000_000_000L,
                1_000_000_000L + PreviewSceneCapturePolicy.MAX_AGE_NANOS));
        assertFalse(PreviewSceneCapturePolicy.isFresh(1_000_000_000L,
                1_000_000_001L + PreviewSceneCapturePolicy.MAX_AGE_NANOS));
        assertFalse(PreviewSceneCapturePolicy.isFresh(Long.MIN_VALUE, 1_000_000_000L));
        assertFalse(PreviewSceneCapturePolicy.isFresh(2_000_000_000L, 1_000_000_000L));
    }
}
