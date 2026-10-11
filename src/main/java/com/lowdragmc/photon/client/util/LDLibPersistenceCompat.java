package com.lowdragmc.photon.client.util;

import com.lowdragmc.lowdraglib.syncdata.TypedPayloadRegistries;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
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
        if (!containsManagedListEnvelope(root)) {
            return root;
        }
        byte nbtPayloadType = TypedPayloadRegistries.getId(NbtTagPayload.class);
        if (nbtPayloadType < 0) {
            throw new IllegalStateException("LDLib NBT payload type is not registered before Photon project load");
        }
        normalize(root, nbtPayloadType);
        return root;
    }

    static CompoundTag normalize(CompoundTag root, byte nbtPayloadType) {
        normalizeTag(root, nbtPayloadType);
        return root;
    }

    private static boolean containsManagedListEnvelope(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            for (var key : compound.getAllKeys()) {
                var child = compound.get(key);
                if (MANAGED_LIST_FIELDS.contains(key)
                        && child instanceof CompoundTag envelope
                        && envelope.contains("payload", Tag.TAG_LIST)) {
                    return true;
                }
                if (containsManagedListEnvelope(child)) {
                    return true;
                }
            }
        } else if (tag instanceof ListTag list) {
            for (var child : list) {
                if (containsManagedListEnvelope(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Tag normalizeTag(Tag tag, byte nbtPayloadType) {
        if (tag instanceof CompoundTag compound) {
            for (var key : new ArrayList<>(compound.getAllKeys())) {
                var child = compound.get(key);
                if (MANAGED_LIST_FIELDS.contains(key)
                        && child instanceof CompoundTag envelope
                        && envelope.contains("payload", Tag.TAG_LIST)) {
                    child = toLegacyArrayPayload(envelope.getList("payload", Tag.TAG_COMPOUND), nbtPayloadType);
                }
                compound.put(key, normalizeTag(child, nbtPayloadType));
            }
        } else if (tag instanceof ListTag list) {
            for (int index = 0; index < list.size(); index++) {
                list.set(index, normalizeTag(list.get(index), nbtPayloadType));
            }
        }
        return tag;
    }

    /** LDLib 1 ArrayPayload requires {@code {t, p}} per element; LDLib2 stores each value directly. */
    private static ListTag toLegacyArrayPayload(ListTag payload, byte nbtPayloadType) {
        var legacyPayload = new ListTag();
        for (var entry : payload) {
            if (entry instanceof CompoundTag compound
                    && compound.contains("t", Tag.TAG_BYTE)
                    && compound.contains("p")) {
                legacyPayload.add(entry.copy());
                continue;
            }
            var wrapped = new CompoundTag();
            wrapped.putByte("t", nbtPayloadType);
            wrapped.put("p", entry.copy());
            legacyPayload.add(wrapped);
        }
        return legacyPayload;
    }
}
