package com.lowdragmc.photon.client.shadergraph.nodes;

import com.lowdragmc.photon.client.postfx.shadergraph.PhotonFullscreenCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenToWorldMatrixTest {
    @Test
    void selectsTheInverseViewMatrixForEachCoordinateSpace() {
        assertEquals("IViewMat", PhotonFullscreenCompiler.inverseViewMatrixName(true));
        assertEquals("IModelViewMat", PhotonFullscreenCompiler.inverseViewMatrixName(false));
    }
}
