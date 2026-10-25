package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

import java.util.Map;
import java.util.Set;

/** Reads and writes the custom shader uniform overrides stored by Photon 26.2. */
final class CustomShaderUniformNbt {
    private static final int MAX_COMPONENTS = 16;
    private static final Set<String> BUILTIN_UNIFORMS = Set.of(
            "ModelViewMat", "ProjMat", "IViewRotMat", "ColorModulator", "FogStart", "FogEnd",
            "FogColor", "FogShape", "GameTime", "ScreenSize", "LineWidth");

    private CustomShaderUniformNbt() {
    }

    static CompoundTag write(Map<String, float[]> overrides) {
        CompoundTag result = new CompoundTag();
        overrides.forEach((name, values) -> {
            if (!validName(name) || !validValues(values)) return;
            ListTag list = new ListTag();
            for (float value : values) list.add(FloatTag.valueOf(value));
            result.put(name, list);
        });
        return result;
    }

    static void readInto(Map<String, float[]> destination, Tag tag) {
        destination.clear();
        if (!(tag instanceof CompoundTag compound)) return;
        for (String name : compound.getAllKeys()) {
            if (!validName(name) || !(compound.get(name) instanceof ListTag list)
                    || list.isEmpty() || list.size() > MAX_COMPONENTS) continue;
            float[] values = new float[list.size()];
            boolean valid = true;
            for (int i = 0; i < values.length; i++) {
                Tag value = list.get(i);
                if (!(value instanceof FloatTag number) || !Float.isFinite(number.getAsFloat())) {
                    valid = false;
                    break;
                }
                values[i] = number.getAsFloat();
            }
            if (valid) destination.put(name, values);
        }
    }

    static void readLegacyUniformsInto(Map<String, float[]> destination, Tag tag) {
        if (!(tag instanceof CompoundTag compound)) return;
        for (String name : compound.getAllKeys()) {
            if (!validName(name) || BUILTIN_UNIFORMS.contains(name)) continue;
            Tag value = compound.get(name);
            if (value instanceof IntArrayTag ints) {
                int[] components = ints.getAsIntArray();
                if (components.length == 0 || components.length > MAX_COMPONENTS) continue;
                float[] converted = new float[components.length];
                for (int i = 0; i < components.length; i++) converted[i] = components[i];
                destination.put(name, converted);
            } else if (value instanceof ListTag list && !list.isEmpty() && list.size() <= MAX_COMPONENTS) {
                float[] components = new float[list.size()];
                boolean valid = true;
                for (int i = 0; i < components.length; i++) {
                    if (!(list.get(i) instanceof NumericTag number) || !Float.isFinite(number.getAsFloat())) {
                        valid = false;
                        break;
                    }
                    components[i] = number.getAsFloat();
                }
                if (valid) destination.put(name, components);
            }
        }
    }

    static boolean isPhoton262LegacyShaderData(CompoundTag shaderData) {
        return !shaderData.contains("hdrVersion")
                && !shaderData.contains("hdrUniforms")
                && !shaderData.contains("samplers");
    }

    private static boolean validName(String name) {
        return name != null && !name.isBlank() && !name.startsWith("U_");
    }

    private static boolean validValues(float[] values) {
        if (values == null || values.length == 0 || values.length > MAX_COMPONENTS) return false;
        for (float value : values) if (!Float.isFinite(value)) return false;
        return true;
    }
}
