package com.lowdragmc.photon.client.gameobject.particle.renderer;

import com.lowdragmc.photon.Photon;

/** Tests the OpenGL minimum texture-buffer bound before render code allocates or uploads its batch. */
public final class PointBufferCapacityRegression {
    private PointBufferCapacityRegression() {
    }

    public static void verify() {
        check(InstancedRenderBackend.fitsPointBuffer(16_384, 4, 65_536),
                "the OpenGL 3.1 guaranteed point-buffer minimum was rejected");
        check(!InstancedRenderBackend.fitsPointBuffer(16_385, 4, 65_536),
                "a point buffer larger than the guaranteed texture-buffer limit was accepted");
        check(InstancedRenderBackend.fitsPointBuffer(100, 4, 1_000),
                "a buffer below the driver limit was rejected");
        check(!InstancedRenderBackend.fitsPointBuffer(251, 4, 1_000),
                "a buffer above the driver limit was accepted");
        check(!InstancedRenderBackend.fitsPointBuffer(Long.MAX_VALUE, 4, Integer.MAX_VALUE),
                "a point capacity that overflows Java staging-buffer size was accepted");
        check(!InstancedRenderBackend.fitsPointBuffer(1, 0, 65_536),
                "a zero-texel point layout was accepted");
        check(InstancedRenderBackend.fitsPointBuffer(8_192, 8, 65_536),
                "a shadergraph data batch at the texture-buffer boundary was rejected");
        check(!InstancedRenderBackend.fitsPointBuffer(8_193, 8, 65_536),
                "an oversized shadergraph data batch was accepted");
        check(InstancedRenderBackend.fitsPointBuffer(16_384, 4, 65_536),
                "a custom-data batch at the texture-buffer boundary was rejected");
        check(!InstancedRenderBackend.fitsPointBuffer(16_385, 4, 65_536),
                "an oversized custom-data batch was accepted");
        check(InstancedRenderBackend.fitsInstanceStaging(Integer.MAX_VALUE / 8, 8),
                "an instance batch at the Java staging boundary was rejected");
        check(!InstancedRenderBackend.fitsInstanceStaging((long) Integer.MAX_VALUE / 8 + 1, 8),
                "an instance batch that overflows its interleaved staging buffer was accepted");
        check(!InstancedRenderBackend.fitsInstanceStaging(1, 0),
                "an instance batch with an invalid zero-float layout was accepted");
        Photon.LOGGER.info("[point-buffer-capacity-smoke] particle, shadergraph and custom-data uploads respect OpenGL texture-buffer and Java staging limits");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
