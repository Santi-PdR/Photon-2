package com.lowdragmc.photon.client.gameobject.emitter.renderpipeline;

import java.util.Arrays;

/** Reusable stable far-to-near ordering for translucent primitive centers. Render-thread only. */
final class PhotonDistanceSort {
    private static float[] keys = new float[0];
    private static long[] packed = new long[0];
    private static int[] order = new int[0];

    private PhotonDistanceSort() {
    }

    static float[] keys(int count) {
        if (keys.length < count) keys = new float[Math.max(count, keys.length * 2)];
        return keys;
    }

    /** Returns indices far-to-near, retaining the original order for equal distances. */
    static int[] farToNear(int count) {
        if (packed.length < count) packed = new long[Math.max(count, packed.length * 2)];
        var distances = keys;
        var sortable = packed;
        for (int i = 0; i < count; i++) {
            sortable[i] = ((long) Float.floatToRawIntBits(distances[i]) << 32) | (0xFFFFFFFFL - i);
        }
        Arrays.sort(sortable, 0, count);
        if (order.length < count) order = new int[Math.max(count, order.length * 2)];
        var result = order;
        for (int i = 0; i < count; i++) {
            result[i] = (int) (0xFFFFFFFFL - (sortable[count - 1 - i] & 0xFFFFFFFFL));
        }
        return result;
    }
}
