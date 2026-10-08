package com.lowdragmc.photon.client.compat.iris.internal;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IrisSceneColorSelectionTest {
    @Test
    void usesMainTextureWhenColortexZeroHasNotFlipped() {
        assertEquals(101, IrisSceneColorSelection.activeTexture(101, 202, Set.of()));
    }

    @Test
    void usesAlternateTextureWhenColortexZeroHasFlipped() {
        assertEquals(202, IrisSceneColorSelection.activeTexture(101, 202, Set.of(0)));
    }

    @Test
    void ignoresFlipStateForOtherColortexBuffers() {
        assertEquals(101, IrisSceneColorSelection.activeTexture(101, 202, Set.of(1, 3)));
    }
}
