package com.lowdragmc.photon.client.postfx.runtime;

import com.lowdragmc.photon.client.postfx.runtime.CompiledEffect.ResourceRef.Source;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderGraphExecutorInputAvailabilityTest {
    @Test
    void missingDepthOrMaskInputsSkipTheEffectInsteadOfBindingStaleTextures() {
        assertFalse(RenderGraphExecutor.requiredTextureAvailable(Source.SCENE_DEPTH, -1, 4, 5));
        assertFalse(RenderGraphExecutor.requiredTextureAvailable(Source.CUSTOM_MASK, 3, -1, 5));
        assertFalse(RenderGraphExecutor.requiredTextureAvailable(Source.CUSTOM_DEPTH, 3, 4, -1));
    }

    @Test
    void availableInputsAndNonOptionalSourcesRemainRunnable() {
        assertTrue(RenderGraphExecutor.requiredTextureAvailable(Source.SCENE_DEPTH, 3, -1, -1));
        assertTrue(RenderGraphExecutor.requiredTextureAvailable(Source.CUSTOM_MASK, -1, 4, -1));
        assertTrue(RenderGraphExecutor.requiredTextureAvailable(Source.CUSTOM_DEPTH, -1, -1, 5));
        assertTrue(RenderGraphExecutor.requiredTextureAvailable(Source.SCENE_COLOR, -1, -1, -1));
        assertTrue(RenderGraphExecutor.requiredTextureAvailable(Source.RESOURCE, -1, -1, -1));
    }
}
