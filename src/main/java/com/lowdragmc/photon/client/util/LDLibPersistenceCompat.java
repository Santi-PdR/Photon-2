package com.lowdragmc.photon.client.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Set;

/**
 * Converts LDLib2's managed-list envelope to the ListTag format read by LDLib 1.0.x's
 * {@code PersistedParser}. Photon 26.2 serializes these fields as {@code {uid, payload}}, while
 * the requested 1.20.1 runtime uses the older parser, which expects the payload list directly.
 */
public final class LDLibPersistenceCompat {
    private static final Set<String> MANAGED_LIST_FIELDS = Set.of(
            "bursts", "materials", "influenceList", "emitters");

    private LDLibPersistenceCompat() {
    }

    public static CompoundTag normalize(CompoundTag root) {
        normalizeTag(root, null);
        return root;
    }

    private static Tag normalizeTag(Tag tag, String fieldName) {
        if (tag instanceof CompoundTag compound) {
            for (var key : new ArrayList<>(compound.getAllKeys())) {
                var child = compound.get(key);
                if (MANAGED_LIST_FIELDS.contains(key)
                        && child instanceof CompoundTag envelope
                        && envelope.contains("payload", Tag.TAG_LIST)) {
                    child = envelope.get("payload");
                }
                compound.put(key, normalizeTag(child, key));
            }
        } else if (tag instanceof ListTag list) {
            for (int index = 0; index < list.size(); index++) {
                list.set(index, normalizeTag(list.get(index), fieldName));
            }
        }
        return tag;
    }
}
