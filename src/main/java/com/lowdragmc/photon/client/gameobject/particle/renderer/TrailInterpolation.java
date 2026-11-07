package com.lowdragmc.photon.client.gameobject.particle.renderer;

/** Guard interpolation against absent, equal or non-finite tail lifetimes, as in Photon 26.2. */
public final class TrailInterpolation {
    private TrailInterpolation() {}

    public static boolean hasDuration(float duration) { return duration > 0f && Float.isFinite(duration); }
    public static float partialTime(float firstLife, float duration, float partialTicks) {
        return hasDuration(duration) ? 1f - (firstLife + 1f - partialTicks) / duration : 0f;
    }
    public static boolean hasPassed(float nextLife, float duration, float time) {
        return hasDuration(duration) && nextLife / duration < time;
    }
    public static boolean shouldInterpolate(float currentLife, float nextLife, float duration, float time) {
        if (!hasDuration(duration)) return false;
        float current = currentLife / duration, next = nextLife / duration;
        return current <= time && time <= next && next > current;
    }
    public static float factor(float currentLife, float nextLife, float duration, float time) {
        float current = currentLife / duration, next = nextLife / duration;
        return (time - current) / (next - current);
    }
}
