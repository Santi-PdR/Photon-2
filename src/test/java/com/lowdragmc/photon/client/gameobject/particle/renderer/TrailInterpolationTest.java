package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailInterpolationTest {
    @Test
    void skipsLifetimeDivisionWhenDurationIsZeroOrInvalid() {
        for (float duration : new float[]{0f, -1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertEquals(0f, TrailInterpolation.partialTime(2f, duration, 0.5f));
            assertFalse(TrailInterpolation.hasPassed(2f, duration, 0.5f));
            assertFalse(TrailInterpolation.shouldInterpolate(1f, 2f, duration, 0.5f));
        }
    }

    @Test
    void preservesValidInterpolationAndRejectsDuplicateLifePoints() {
        float partialTime = TrailInterpolation.partialTime(1f, 4f, 0.5f);
        assertEquals(0.625f, partialTime, 1e-6f);
        assertTrue(TrailInterpolation.shouldInterpolate(2f, 3f, 4f, partialTime));
        assertEquals(0.5f, TrailInterpolation.factor(2f, 3f, 4f, partialTime), 1e-6f);

        assertFalse(TrailInterpolation.shouldInterpolate(1f, 1f, 2f, 0.5f));
    }
}
