package com.lowdragmc.photon.client.gameobject.particle.renderer;

/** Finite lifetime interpolation shared by the CPU and instanced trail paths. */
final class TrailInterpolation {
    private TrailInterpolation() {}

    static boolean hasDuration(float duration) {
        return duration > 0f && Float.isFinite(duration);
    }

    static float partialTime(float firstLife, float duration, float partialTicks) {
        if (!hasDuration(duration)) return 0f;
        return 1f - (firstLife + 1f - partialTicks) / duration;
    }

    static boolean hasPassed(float nextLife, float duration, float partialTime) {
        return hasDuration(duration) && nextLife / duration < partialTime;
    }

    static boolean shouldInterpolate(float currentLife, float nextLife, float duration, float partialTime) {
        if (!hasDuration(duration)) return false;
        float current = currentLife / duration;
        float next = nextLife / duration;
        return current <= partialTime && partialTime <= next && next > current;
    }

    static float factor(float currentLife, float nextLife, float duration, float partialTime) {
        float current = currentLife / duration;
        float next = nextLife / duration;
        return (partialTime - current) / (next - current);
    }
}
