package com.lowdragmc.photon.client.fx;

/**
 * Per-FX bloom for the current particle build.
 * <p>
 * Bloom is a whole-frame pass, so several effects in one shot share it: each FX that has bloom
 * enabled contributes its intensity, and the pass uses the strongest. An FX with bloom off does
 * not contribute. Intensity lives on the FX (see {@link FXData}), not on the client config.
 * <p>
 * A build that never called {@link #beginFrame()} (the smoke tests drive the post-process alone)
 * falls back to {@link FXData#DEFAULT_BLOOM_INTENSITY} so the chain still has a halo to prove.
 */
public final class FXBloom {
    private static boolean frameActive;
    private static boolean anyEnabled;
    private static float maxIntensity;

    private FXBloom() {
    }

    public static void beginFrame() {
        frameActive = true;
        anyEnabled = false;
        maxIntensity = 0f;
    }

    public static void endFrame() {
        frameActive = false;
        anyEnabled = false;
        maxIntensity = 0f;
    }

    public static void contribute(FXData data) {
        if (data == null || !data.bloomEnabled()) {
            return;
        }
        anyEnabled = true;
        maxIntensity = Math.max(maxIntensity, data.bloomIntensity());
    }

    /**
     * In-world the FX root is a no-draw particle on a different render type than the emitters,
     * so its {@code updateFrame} often has not contributed before the bloom pass. Pull intensity
     * from every live executor instead.
     */
    public static void contributePlaying() {
        for (var effects : EntityEffectExecutor.CACHE.values()) {
            for (var effect : effects) {
                if (effect.runtime != null) {
                    contribute(effect.runtime.getFxData());
                }
            }
        }
        for (var effects : BlockEffectExecutor.CACHE.values()) {
            for (var effect : effects) {
                if (effect.runtime != null) {
                    contribute(effect.runtime.getFxData());
                }
            }
        }
    }

    /**
     * Intensity the bloom pass should apply right now. {@code 0} means skip the halo
     * (every FX in this build turned bloom off).
     */
    public static float intensity() {
        if (!frameActive) {
            return FXData.DEFAULT_BLOOM_INTENSITY;
        }
        return anyEnabled ? maxIntensity : 0f;
    }
}
