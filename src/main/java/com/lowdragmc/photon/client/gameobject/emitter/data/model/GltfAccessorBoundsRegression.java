package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.lowdragmc.photon.Photon;

/** Regression for glTF accessor arithmetic and declared buffer-view boundaries. */
public final class GltfAccessorBoundsRegression {
    private GltfAccessorBoundsRegression() {
    }

    public static void verify() {
        check(GltfMeshParser.accessorRangeFits(4, 12, 16, 0, 3, 4, 4),
                "an accessor exactly inside its view was rejected");
        check(!GltfMeshParser.accessorRangeFits(4, 12, 16, 8, 2, 4, 4),
                "an accessor extending past its buffer view was accepted");
        check(!GltfMeshParser.accessorRangeFits(0, 16, 16, -1, 1, 4, 4),
                "a negative accessor byte offset was accepted");
        check(!GltfMeshParser.accessorRangeFits(0, 16, 16, 0, 2, 2, 4),
                "a stride smaller than the element was accepted");
        check(!GltfMeshParser.accessorRangeFits(Integer.MAX_VALUE - 4, 16,
                        Integer.MAX_VALUE, 0, 2, 4, 4),
                "a view whose end overflows the backing buffer was accepted");
        check(!GltfMeshParser.accessorRangeFits(0, Integer.MAX_VALUE,
                        Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                        Integer.MAX_VALUE, 4),
                "accessor range arithmetic that overflows int was accepted");
        Photon.LOGGER.info("[gltf-accessor-bounds-smoke] accessor offsets and strides stay inside declared buffer views");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
