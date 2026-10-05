package com.lowdragmc.photon.client.gameobject.emitter.renderpipeline;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class PhotonDistanceSortTest {
    @Test
    void ordersPrimitiveCentersFarToNear() {
        var distances = PhotonDistanceSort.keys(3);
        distances[0] = 1f;
        distances[1] = 9f;
        distances[2] = 4f;

        assertArrayEquals(new int[]{1, 2, 0}, firstEntries(3));
    }

    @Test
    void preservesInputOrderForEqualDistances() {
        var distances = PhotonDistanceSort.keys(4);
        distances[0] = 4f;
        distances[1] = 4f;
        distances[2] = 9f;
        distances[3] = 4f;

        assertArrayEquals(new int[]{2, 0, 1, 3}, firstEntries(4));
    }

    @Test
    void handlesEmptyAndSinglePrimitiveLists() {
        assertArrayEquals(new int[0], firstEntries(0));

        PhotonDistanceSort.keys(1)[0] = 2f;
        assertArrayEquals(new int[]{0}, firstEntries(1));
    }

    private static int[] firstEntries(int count) {
        return Arrays.copyOf(PhotonDistanceSort.farToNear(count), count);
    }
}
