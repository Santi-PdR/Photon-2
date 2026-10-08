package com.lowdragmc.photon.client.gameobject.emitter.data.shape;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AreaWeightedSelectionTest {
    private static final double[] WEIGHTS = {1d, 3d};

    @Test
    void selectsByCumulativeAreaAndKeepsTheEarlierTriangleAtAnExactBoundary() {
        assertEquals(0, select(0d));
        assertEquals(0, select(0.25d));
        assertEquals(1, select(Math.nextUp(0.25d)));
        assertEquals(1, select(0.5d));
        assertEquals(1, select(1d));
    }

    @Test
    void skipsZeroAndNegativeWeights() {
        double[] weights = {0d, 2d, -1d, 2d};

        assertEquals(1, AreaWeightedSelection.index(0d, weights.length, 4d, i -> weights[i]));
        assertEquals(3, AreaWeightedSelection.index(0.75d, weights.length, 4d, i -> weights[i]));
    }

    @Test
    void returnsNoSelectionWhenThereAreNoPositiveEntries() {
        assertEquals(-1, AreaWeightedSelection.index(0.5d, 0, 0d, i -> 1d));
        assertEquals(-1, AreaWeightedSelection.index(0.5d, 2, 0d, i -> 0d));
        assertEquals(-1, AreaWeightedSelection.index(0.5d, 2, -1d, i -> 1d));
    }

    private static int select(double sample) {
        return AreaWeightedSelection.index(sample, WEIGHTS.length, 4d, i -> WEIGHTS[i]);
    }
}
