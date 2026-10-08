package com.lowdragmc.photon.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

/** Adapts LDLib2 26.2 vector-list gradient NBT to the flat-list form used by Forge 1.20.1. */
public final class GradientNbtCompat {
    private GradientNbtCompat() {}

    /** Returns a copy that {@code GradientColor.deserializeNBT} from LDLib2 2.2.x can read. */
    public static CompoundTag toForgeGradient(CompoundTag source) {
        var result = source.copy();
        flattenVectorList(result, "a", 2);
        flattenVectorList(result, "rgb", 4);
        return result;
    }

    private static void flattenVectorList(CompoundTag tag, String key, int coordinates) {
        if (!(tag.get(key) instanceof ListTag vectors) || vectors.getElementType() != Tag.TAG_LIST) return;

        var flattened = new ListTag();
        for (Tag entry : vectors) {
            if (!(entry instanceof ListTag vector) || vector.size() != coordinates) continue;
            boolean numeric = true;
            for (Tag coordinate : vector) numeric &= coordinate instanceof NumericTag;
            if (!numeric) continue;
            for (Tag coordinate : vector) {
                flattened.add(FloatTag.valueOf(((NumericTag) coordinate).getAsFloat()));
            }
        }
        tag.put(key, flattened);
    }
}
