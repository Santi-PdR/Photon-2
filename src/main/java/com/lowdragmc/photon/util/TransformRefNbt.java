package com.lowdragmc.photon.util;

import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.TransformRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;

/** LDLib 1.0.52.a-compatible persistence for LDLib2 scene references. */
public final class TransformRefNbt {
    public static final String CUSTOM_SPACE_KEY = "customSpace";

    private TransformRefNbt() {
    }

    public static void writeCustomSpace(CompoundTag tag, TransformRef ref) {
        // TransformRef is an LDLib2 value, not a registered LDLib 1 managed payload.
        tag.put(CUSTOM_SPACE_KEY, ref.serializeNBT());
    }

    public static void readCustomSpace(CompoundTag tag, TransformRef ref) {
        if (tag.get(CUSTOM_SPACE_KEY) instanceof StringTag serialized) {
            ref.deserializeNBT(serialized);
        }
    }
}
