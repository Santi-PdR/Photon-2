package com.lowdragmc.photon.client.util;

import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import org.joml.Vector4f;

/** Conversion helpers for LDLib2 1.20.1's legacy HDR vector: base RGB in xyz, intensity in w. */
public final class HDRColorCompat {
    public static final Codec<Vector4f> CODEC = LDLibExtraCodecs.VECTOR4F;

    private HDRColorCompat() {
    }

    public static Vector4f white() {
        return new Vector4f(1f, 1f, 1f, 1f);
    }

    /** Convert LDLib2's 26.2 RGBA/intensity value to the 1.20.1 persisted RGB/intensity vector. */
    public static HDRColor toHDRColor(Vector4f color, float alpha) {
        return new HDRColor(color.x, color.y, color.z, alpha, color.w);
    }

    public static void premultiplied(Vector4f baseIntensity, float alpha, Vector4f out) {
        premultiplied(baseIntensity.x, baseIntensity.y, baseIntensity.z, baseIntensity.w, alpha, out);
    }

    public static void premultiplied(float red, float green, float blue, float intensity,
                                     float alpha, Vector4f out) {
        out.set(red * intensity, green * intensity, blue * intensity, alpha);
    }

    public static void lerpPremultiplied(Vector4f a, float alphaA, Vector4f b, float alphaB,
                                         float amount, Vector4f out) {
        lerpPremultiplied(a.x, a.y, a.z, a.w, alphaA, b.x, b.y, b.z, b.w, alphaB, amount, out);
    }

    public static void lerpPremultiplied(float redA, float greenA, float blueA, float intensityA,
                                         float alphaA, float redB, float greenB, float blueB,
                                         float intensityB, float alphaB, float amount, Vector4f out) {
        out.set(Mth.lerp(amount, redA * intensityA, redB * intensityB),
                Mth.lerp(amount, greenA * intensityA, greenB * intensityB),
                Mth.lerp(amount, blueA * intensityA, blueB * intensityB),
                Mth.lerp(amount, alphaA, alphaB));
    }

    /** Reads the component layout used by the earlier Vector4f persisted representation. */
    public static Vector4f fromLegacyTag(CompoundTag tag, String key, Vector4f fallback) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) return new Vector4f(fallback);
        var components = tag.getCompound(key);
        return new Vector4f(component(components, "x", "r", fallback.x),
                component(components, "y", "g", fallback.y),
                component(components, "z", "b", fallback.z),
                component(components, "w", "intensity", fallback.w));
    }

    private static float component(CompoundTag tag, String primary, String alternate, float fallback) {
        if (tag.contains(primary, Tag.TAG_FLOAT)) return tag.getFloat(primary);
        if (tag.contains(alternate, Tag.TAG_FLOAT)) return tag.getFloat(alternate);
        return fallback;
    }

    public static Vector4f black() {
        return new Vector4f(0f, 0f, 0f, 1f);
    }

    public static Vector4f fromARGB(int argb) {
        return new Vector4f(
                ((argb >>> 16) & 0xFF) / 255f,
                ((argb >>> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f,
                1f);
    }

    public static Vector4f fromPremultiplied(Vector4f value) {
        return fromPremultiplied(value.x, value.y, value.z);
    }

    public static Vector4f fromPremultiplied(float r, float g, float b) {
        float intensity = Math.max(r, Math.max(g, b));
        if (intensity > 1f) {
            return new Vector4f(r / intensity, g / intensity, b / intensity, intensity);
        }
        return new Vector4f(r, g, b, 1f);
    }

    public static Vector4f withIntensity(Vector4f value, float intensity) {
        return new Vector4f(value.x, value.y, value.z, Math.max(0f, intensity));
    }

    public static Vector4f premultiplied(Vector4f value) {
        return new Vector4f(value.x * value.w, value.y * value.w, value.z * value.w, 1f);
    }

    public static int baseARGB(Vector4f value) {
        return pack(value.x, value.y, value.z);
    }

    public static int toARGB(Vector4f value) {
        return pack(value.x * value.w, value.y * value.w, value.z * value.w);
    }

    private static int pack(float r, float g, float b) {
        return 0xFF000000
                | (Mth.clamp(Math.round(r * 255f), 0, 255) << 16)
                | (Mth.clamp(Math.round(g * 255f), 0, 255) << 8)
                | Mth.clamp(Math.round(b * 255f), 0, 255);
    }
}
