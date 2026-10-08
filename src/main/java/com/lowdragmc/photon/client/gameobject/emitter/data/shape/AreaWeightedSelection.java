package com.lowdragmc.photon.client.gameobject.emitter.data.shape;

import java.util.function.IntToDoubleFunction;

/** Selects a positive-weight entry from a normalized sample using its cumulative weight interval. */
final class AreaWeightedSelection {
    private AreaWeightedSelection() {}

    static int index(double normalizedSample, int count, double totalWeight,
                     IntToDoubleFunction weightAt) {
        if (count <= 0 || !(totalWeight > 0d)) return -1;
        double sample = normalizedSample * totalWeight;
        double cumulative = 0d;
        int lastPositive = -1;
        for (int i = 0; i < count; i++) {
            double weight = weightAt.applyAsDouble(i);
            if (!(weight > 0d)) continue;
            lastPositive = i;
            if (sample <= cumulative + weight) return i;
            cumulative += weight;
        }
        return lastPositive;
    }
}
