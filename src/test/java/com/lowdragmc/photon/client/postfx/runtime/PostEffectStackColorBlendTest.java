package com.lowdragmc.photon.client.postfx.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostEffectStackColorBlendTest {
    @Test
    void blendsArgbChannelsIndependently() {
        assertEquals(0xFF808080, PostEffectStack.lerpArgb(0xFF000000, 0xFFFFFFFF, 0.5f));
    }

    @Test
    void clampsEveryArgbChannelForWeightsOutsideUnitRange() {
        assertEquals(0xFFFFFFFF, PostEffectStack.lerpArgb(0xFF000000, 0xFFFFFFFF, 2f));
        assertEquals(0x00000000, PostEffectStack.lerpArgb(0xFFFFFFFF, 0x00000000, 2f));
    }
}
