package com.lowdragmc.photon.client.compat.iris.internal;

import java.util.Set;

/** Maps Oculus' colortex0 flip state to the texture that currently contains scene colour. */
final class IrisSceneColorSelection {
    private IrisSceneColorSelection() {
    }

    static int activeTexture(int mainTexture, int alternateTexture, Set<Integer> flippedBuffers) {
        return flippedBuffers.contains(0) ? alternateTexture : mainTexture;
    }
}
