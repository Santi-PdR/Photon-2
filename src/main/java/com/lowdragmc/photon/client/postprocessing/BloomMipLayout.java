package com.lowdragmc.photon.client.postprocessing;

import java.util.ArrayList;
import java.util.List;

/** Pure target-size planning for the bloom pyramid. */
final class BloomMipLayout {
    record Size(int width, int height) {}

    private BloomMipLayout() {}

    static List<Size> compute(int width, int height, int requestedLevels) {
        int levels = Math.max(1, Math.min(10, requestedLevels));
        int mipWidth = Math.max(1, width / 2);
        int mipHeight = Math.max(1, height / 2);
        var result = new ArrayList<Size>();
        for (int i = 0; i < levels && mipWidth >= 8 && mipHeight >= 8; i++) {
            result.add(new Size(mipWidth, mipHeight));
            mipWidth /= 2;
            mipHeight /= 2;
        }
        return List.copyOf(result);
    }
}
