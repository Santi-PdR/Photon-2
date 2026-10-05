package com.lowdragmc.photon.client.postfx.runtime;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.SamplerDefault;
import com.lowdragmc.photon.client.PhotonSamplerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

    @Test
    void appliesMaterialSamplerOverrideToItsCompiledSamplerUnit() {
        var override = new RenderTypeGraphTypes.Sampler2DValue("minecraft:textures/misc/unknown.png",
                RenderTypeGraphTypes.SamplerMode.CUSTOM, RenderTypeGraphTypes.SamplerFilter.NEAREST,
                RenderTypeGraphTypes.SamplerAddress.REPEAT, false);

        var bindings = PhotonSamplerState.overrides(List.of("Sampler0"), Map.of("Albedo", "Sampler0"),
                Map.of("Sampler0", SamplerDefault.missing()), Map.of("Albedo", override));

        assertEquals(1, bindings.size());
        assertEquals(0, bindings.get(0).unit());
        assertEquals(0x2600, bindings.get(0).sampler().minFilter()); // override replaced graph default
        assertEquals(0x2901, bindings.get(0).sampler().wrapS());
    }

    private static com.lowdragmc.kilagraph.rendertype.compiler.KGSamplerGl.GlSampler sampler(
            RenderTypeGraphTypes.SamplerFilter filter,
            RenderTypeGraphTypes.SamplerAddress address, boolean mipmap) {
        var value = new RenderTypeGraphTypes.Sampler2DValue("minecraft:textures/misc/unknown.png",
                RenderTypeGraphTypes.SamplerMode.CUSTOM, filter, address, mipmap);
        return PhotonSamplerState.from(value);
    }
}
