package com.lowdragmc.photon.client.postfx.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderGraphExecutorTexelSizeTest {
    @Test
    void unknownExternalTextureDimensionsClearThePreviousSamplerSize() {
        assertEquals(new RenderGraphExecutor.TexelSizeValues(0, 0, 0, 0),
                RenderGraphExecutor.texelSizeValues(0, 0));
        assertEquals(new RenderGraphExecutor.TexelSizeValues(0, 0, 0, 0),
                RenderGraphExecutor.texelSizeValues(-32, 64));
    }

    @Test
    void knownTextureDimensionsIncludeTheirReciprocals() {
        assertEquals(new RenderGraphExecutor.TexelSizeValues(640, 360, 1f / 640, 1f / 360),
                RenderGraphExecutor.texelSizeValues(640, 360));
    }
}
