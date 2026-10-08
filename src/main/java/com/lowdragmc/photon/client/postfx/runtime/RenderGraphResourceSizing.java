package com.lowdragmc.photon.client.postfx.runtime;

import java.util.List;

/** Resolves each effect's transient targets against the original frame size. */
final class RenderGraphResourceSizing {
    private RenderGraphResourceSizing() {
    }

    static Sizes resolve(List<CompiledEffect.ResourceDesc> resources, int frameWidth, int frameHeight) {
        int[] widths = new int[resources.size()];
        int[] heights = new int[resources.size()];
        for (int i = 0; i < resources.size(); i++) {
            var size = resources.get(i).size();
            switch (size.mode()) {
                case SCREEN_RELATIVE -> {
                    widths[i] = scaled(frameWidth, size.scale());
                    heights[i] = scaled(frameHeight, size.scale());
                }
                case INPUT_RELATIVE -> {
                    // The compiler ensures referenced resources resolve before their consumers.
                    widths[i] = scaled(widths[size.inputResource()], size.scale());
                    heights[i] = scaled(heights[size.inputResource()], size.scale());
                }
                case ABSOLUTE -> {
                    widths[i] = Math.max(1, size.width());
                    heights[i] = Math.max(1, size.height());
                }
            }
        }
        return new Sizes(widths, heights);
    }

    private static int scaled(int dimension, float scale) {
        return Math.max(1, Math.round(dimension * scale));
    }

    record Sizes(int[] widths, int[] heights) {
    }
}
