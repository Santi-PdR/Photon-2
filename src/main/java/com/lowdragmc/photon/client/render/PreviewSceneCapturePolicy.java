package com.lowdragmc.photon.client.render;

/** Pure freshness policy for reusing the prior frame's scene sampler textures in GUI previews. */
public final class PreviewSceneCapturePolicy {
    public static final long MAX_AGE_NANOS = 250_000_000L;

    private PreviewSceneCapturePolicy() {}

    public static boolean isFresh(long capturedAtNanos, long nowNanos) {
        return capturedAtNanos != Long.MIN_VALUE && nowNanos >= capturedAtNanos
                && nowNanos - capturedAtNanos <= MAX_AGE_NANOS;
    }
}
