package com.lowdragmc.photon.client.util;

import com.lowdragmc.lowdraglib2.math.HDRColor;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HDRColorCompatTest {
    private static final float EPSILON = 1.0e-6f;

    @Test
    void roundTripsRgbaAndIntensityThroughTheLegacyVector() {
        var original = new HDRColor(0.25f, 0.5f, 0.75f, 0.35f, 3.5f);

        var legacy = new Vector4f(original.getR(), original.getG(), original.getB(), original.getIntensity());
        var restored = HDRColorCompat.toHDRColor(legacy, original.getA());

        assertEquals(original.getR(), restored.getR(), EPSILON);
        assertEquals(original.getG(), restored.getG(), EPSILON);
        assertEquals(original.getB(), restored.getB(), EPSILON);
        assertEquals(original.getA(), restored.getA(), EPSILON);
        assertEquals(original.getIntensity(), restored.getIntensity(), EPSILON);
    }

    @Test
    void constantSamplingPremultipliesRgbButPreservesAlpha() {
        var out = new Vector4f();

        HDRColorCompat.premultiplied(new Vector4f(0.25f, 0.5f, 0.75f, 2f), 0.4f, out);

        assertEquals(0.5f, out.x, EPSILON);
        assertEquals(1f, out.y, EPSILON);
        assertEquals(1.5f, out.z, EPSILON);
        assertEquals(0.4f, out.w, EPSILON);
    }

    @Test
    void randomSamplingInterpolatesPremultipliedRgbAndAlpha() {
        var colorA = new Vector4f(0.5f, 0.25f, 0f, 2f);
        var colorB = new Vector4f(0f, 0.5f, 1f, 4f);
        var out = new Vector4f();

        HDRColorCompat.lerpPremultiplied(colorA, 0.2f, colorB, 1f, 0.25f, out);

        assertEquals(0.75f, out.x, EPSILON);
        assertEquals(0.875f, out.y, EPSILON);
        assertEquals(1f, out.z, EPSILON);
        assertEquals(0.4f, out.w, EPSILON);
    }
}
