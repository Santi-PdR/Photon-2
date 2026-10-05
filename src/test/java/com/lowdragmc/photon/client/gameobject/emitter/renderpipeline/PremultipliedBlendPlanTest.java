package com.lowdragmc.photon.client.gameobject.emitter.renderpipeline;

import com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PremultipliedBlendPlanTest {
    @Test
    void acceptsAlphaOver() {
        assertTrue(PremultipliedBlendPlan.isLayerSafe(
                new BlendMode(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, BlendMode.BlendFuc.ADD)));
    }

    @Test
    void acceptsAdditiveWithCoveragePreserved() {
        assertTrue(PremultipliedBlendPlan.isLayerSafe(
                new BlendMode(SourceFactor.SRC_ALPHA, DestFactor.ONE, BlendMode.BlendFuc.ADD)));
    }

    @Test
    void acceptsReverseSubtractOnlyWithUnitDestination() {
        assertTrue(PremultipliedBlendPlan.isLayerSafe(
                new BlendMode(SourceFactor.SRC_ALPHA, DestFactor.ONE, BlendMode.BlendFuc.REVERSE_SUB)));
        assertFalse(PremultipliedBlendPlan.isLayerSafe(
                new BlendMode(SourceFactor.SRC_ALPHA, DestFactor.ZERO, BlendMode.BlendFuc.REVERSE_SUB)));
    }

    @Test
    void rejectsSourceAlphaSaturateBecauseItReadsDestinationAlpha() {
        assertFalse(PremultipliedBlendPlan.isLayerSafe(
                new BlendMode(SourceFactor.SRC_ALPHA_SATURATE, DestFactor.ONE_MINUS_SRC_ALPHA,
                        BlendMode.BlendFuc.ADD)));
    }

    @Test
    void rejectsOtherDestinationDependentFactors() {
        assertFalse(PremultipliedBlendPlan.isLayerSafe(
                new BlendMode(SourceFactor.DST_COLOR, DestFactor.ONE, BlendMode.BlendFuc.ADD)));
        assertFalse(PremultipliedBlendPlan.isLayerSafe(
                new BlendMode(SourceFactor.SRC_ALPHA, DestFactor.DST_COLOR, BlendMode.BlendFuc.ADD)));
    }

    @Test
    void rejectsDisabledBlendingAndUnsupportedEquations() {
        var disabled = new BlendMode();
        disabled.setEnableBlend(false);
        assertFalse(PremultipliedBlendPlan.isLayerSafe(disabled));
        assertFalse(PremultipliedBlendPlan.isLayerSafe(
                new BlendMode(SourceFactor.SRC_ALPHA, DestFactor.ONE, BlendMode.BlendFuc.MIN)));
        assertFalse(PremultipliedBlendPlan.isLayerSafe(
                new BlendMode(SourceFactor.SRC_ALPHA, DestFactor.ONE, BlendMode.BlendFuc.MAX)));
    }
}
