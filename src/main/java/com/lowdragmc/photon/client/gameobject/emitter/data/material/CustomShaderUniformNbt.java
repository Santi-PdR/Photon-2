package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Map;

/** Pure NBT adapter for per-material custom shader uniform overrides. */
final class CustomShaderUniformNbt {
    private static final int MAX_COMPONENTS = 16;

    private CustomShaderUniformNbt() {}

    static CompoundTag write(Map<String, float[]> overrides) {
        var result = new CompoundTag();
        overrides.forEach((name, values) -> {
            if (!validName(name) || !validValues(values)) return;
            var list = new ListTag();
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
            var values = new float[list.size()];
            boolean valid = true;
            for (int i = 0; i < values.length; i++) {
                if (!(list.get(i) instanceof FloatTag value) || !Float.isFinite(value.getAsFloat())) {
                    valid = false;
                    break;
                }
                values[i] = value.getAsFloat();
            }
            if (valid) destination.put(name, values);
        }
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
