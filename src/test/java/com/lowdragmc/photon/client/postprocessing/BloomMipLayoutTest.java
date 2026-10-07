package com.lowdragmc.photon.client.postprocessing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BloomMipLayoutTest {
    @Test
    void startsAtHalfResolutionAndStopsAtRequestedLevelCount() {
        assertEquals(List.of(
                new BloomMipLayout.Size(960, 540),
                new BloomMipLayout.Size(480, 270),
                new BloomMipLayout.Size(240, 135),
                new BloomMipLayout.Size(120, 67),
                new BloomMipLayout.Size(60, 33)),
                BloomMipLayout.compute(1920, 1080, 5));
    }

    @Test
    void stopsBeforeEitherMipDimensionFallsBelowEight() {
        assertEquals(List.of(
                new BloomMipLayout.Size(32, 32),
                new BloomMipLayout.Size(16, 16),
                new BloomMipLayout.Size(8, 8)),
                BloomMipLayout.compute(64, 64, 10));
        assertEquals(List.of(), BloomMipLayout.compute(15, 15, 5));
    }

    @Test
    void clampsRequestedLevelsToUpstreamBounds() {
        assertEquals(1, BloomMipLayout.compute(1024, 1024, 0).size());
        assertEquals(7, BloomMipLayout.compute(1024, 1024, 20).size());
    }
}
