package com.lowdragmc.photon.client.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FXRuntimeRateTest {
    @Test
    void preservesFiniteRatesAboveTheSimulationSubstepBudget() {
        assertEquals(0f, FXRuntime.normalizeRate(0f));
        assertEquals(0f, FXRuntime.normalizeRate(-2f));
        assertEquals(0.5f, FXRuntime.normalizeRate(0.5f));
        assertEquals(1f, FXRuntime.normalizeRate(1f));
        assertEquals(100f, FXRuntime.normalizeRate(100f));
    }

    @Test
    void nonFiniteRateRestoresNormalPlayback() {
        assertEquals(1f, FXRuntime.normalizeRate(Float.NaN));
        assertEquals(1f, FXRuntime.normalizeRate(Float.POSITIVE_INFINITY));
        assertEquals(1f, FXRuntime.normalizeRate(Float.NEGATIVE_INFINITY));
    }
}
