package com.lowdragmc.photon.client.postfx.runtime;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalSamplerStateTest {
    @Test
    void mapsFilterAddressAndMipmapToOpenGlSamplerParameters() {
        var linearRepeatMipmap = sampler(RenderTypeGraphTypes.SamplerFilter.LINEAR,
                RenderTypeGraphTypes.SamplerAddress.REPEAT, true);
        var nearestClamp = sampler(RenderTypeGraphTypes.SamplerFilter.NEAREST,
                RenderTypeGraphTypes.SamplerAddress.CLAMP, false);

        assertEquals(0x2703, linearRepeatMipmap.minFilter()); // GL_LINEAR_MIPMAP_LINEAR
        assertEquals(0x2601, linearRepeatMipmap.magFilter()); // GL_LINEAR
        assertEquals(0x2901, linearRepeatMipmap.wrapS()); // GL_REPEAT
        assertEquals(0x2901, linearRepeatMipmap.wrapT());
        assertEquals(0x2600, nearestClamp.minFilter()); // GL_NEAREST
        assertEquals(0x2600, nearestClamp.magFilter());
        assertEquals(0x812F, nearestClamp.wrapS()); // GL_CLAMP_TO_EDGE
        assertEquals(0x812F, nearestClamp.wrapT());
    }

    private static com.lowdragmc.kilagraph.rendertype.compiler.KGSamplerGl.GlSampler sampler(
            RenderTypeGraphTypes.SamplerFilter filter,
            RenderTypeGraphTypes.SamplerAddress address, boolean mipmap) {
        var value = new RenderTypeGraphTypes.Sampler2DValue("minecraft:textures/misc/unknown.png",
                RenderTypeGraphTypes.SamplerMode.CUSTOM, filter, address, mipmap);
        return RenderGraphExecutor.samplerState(value);
    }
}
