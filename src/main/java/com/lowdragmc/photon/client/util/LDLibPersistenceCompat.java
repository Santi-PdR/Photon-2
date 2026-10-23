package com.lowdragmc.photon.client.util;

import com.lowdragmc.lowdraglib.syncdata.TypedPayloadRegistries;
import com.lowdragmc.lowdraglib.syncdata.payload.ArrayPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.StringPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Converts LDLib2's managed-list envelopes to the ListTag format read by LDLib 1.0.x's
 * {@code PersistedParser}. Photon 26.2 serializes them as {@code {uid, payload}}, while the
 * requested 1.20.1 runtime uses the older parser, which expects the payload list directly.
 */
public final class LDLibPersistenceCompat {
    /** Persisted Photon Vector3f fields which LDLib 2 writes as three-float lists. */
    private static final Set<String> VECTOR3F_FIELDS = Set.of(
            "customDirection", "end", "gravity", "initialVelocity", "modelPivot");

    private LDLibPersistenceCompat() {
    }

    /** Encode object-list compounds using the legacy wrapper format expected by LDLib 1.0.x. */
    public static ListTag encodePayloadList(List<CompoundTag> entries) {
        var payloads = new ITypedPayload<?>[entries.size()];
        for (int index = 0; index < entries.size(); index++) {
            payloads[index] = NbtTagPayload.of(entries.get(index));
        }
        return (ListTag) ArrayPayload.of(payloads).serializeNBT();
    }

    /** Decode an LDLib 1.0.x ArrayPayload into its compound entries. */
    public static List<CompoundTag> decodePayloadList(ListTag list) {
        var arrayPayload = new ArrayPayload();
        arrayPayload.deserializeNBT(list);
        var entries = new ArrayList<CompoundTag>();
        for (var payload : arrayPayload.getPayload()) {
            var value = payload.getPayload();
            if (!(value instanceof CompoundTag compound)) {
                throw new IllegalArgumentException("Managed list entry is not a compound tag: " + value);
            }
            entries.add(compound);
        }
        return entries;
    }

    public static CompoundTag normalize(CompoundTag root) {
        if (!containsCompatibilityData(root)) {
            return root;
        }
        byte nbtPayloadType = containsManagedListEnvelope(root)
                ? TypedPayloadRegistries.getId(NbtTagPayload.class) : -1;
        if (containsManagedListEnvelope(root) && nbtPayloadType < 0) {
            throw new IllegalStateException("LDLib NBT payload type is not registered before Photon project load");
        }
        byte stringPayloadType = containsAdditionalDataStringSet(root)
                ? TypedPayloadRegistries.getId(StringPayload.class) : -1;
        if (containsAdditionalDataStringSet(root) && stringPayloadType < 0) {
            throw new IllegalStateException("LDLib string payload type is not registered before Photon project load");
        }
        normalize(root, nbtPayloadType, stringPayloadType);
        return root;
    }

    static CompoundTag normalize(CompoundTag root, byte nbtPayloadType) {
        byte stringPayloadType = containsAdditionalDataStringSet(root)
                ? TypedPayloadRegistries.getId(StringPayload.class) : -1;
        if (containsAdditionalDataStringSet(root) && stringPayloadType < 0) {
            throw new IllegalStateException("LDLib string payload type is not registered before Photon project load");
        }
        normalize(root, nbtPayloadType, stringPayloadType);
        return root;
    }

    private static void normalize(CompoundTag root, byte nbtPayloadType, byte stringPayloadType) {
        normalizeTag(root, nbtPayloadType, stringPayloadType);
    }

    private static boolean containsManagedListEnvelope(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            for (var key : compound.getAllKeys()) {
                var child = compound.get(key);
                if (isManagedListEnvelope(child) || containsManagedListEnvelope(child)) {
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

    private static boolean containsCompatibilityData(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            for (var key : compound.getAllKeys()) {
                var child = compound.get(key);
                if (isManagedListEnvelope(child) || isVector3fField(key, child)
                        || isAdditionalDataStringSet(key, child)) {
                    return true;
                }
                if (containsCompatibilityData(child)) {
                    return true;
                }
            }
        } else if (tag instanceof ListTag list) {
            for (var child : list) {
                if (containsCompatibilityData(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsAdditionalDataStringSet(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            for (var key : compound.getAllKeys()) {
                var child = compound.get(key);
                if (isAdditionalDataStringSet(key, child)) return true;
                if (isManagedListEnvelope(child)) {
                    var payload = ((CompoundTag) child).get("payload");
                    if (isAdditionalDataStringSet(key, payload)) return true;
                }
                if (containsAdditionalDataStringSet(child)) return true;
            }
        } else if (tag instanceof ListTag list) {
            for (var child : list) {
                if (containsAdditionalDataStringSet(child)) return true;
            }
        }
        return false;
    }

    private static Tag normalizeTag(Tag tag, byte nbtPayloadType, byte stringPayloadType) {
        if (tag instanceof CompoundTag compound) {
            for (var key : new ArrayList<>(compound.getAllKeys())) {
                var child = compound.get(key);
                if (isVector3fField(key, child)) {
                    child = toLegacyVector3f((ListTag) child);
                } else if (isManagedListEnvelope(child)) {
                    var envelope = (CompoundTag) child;
                    var payload = (ListTag) envelope.get("payload");
                    // LDLib2 ReadOnlyManaged fields can use a custom serializer that returns a
                    // ListTag directly (Photon's force-field influenceList stores UUID strings).
                    // Preserve those scalar entries for LDLib 1's matching custom deserializer;
                    // object collections instead need the legacy ArrayPayload element wrappers.
                    if (isAdditionalDataStringSet(key, payload)) {
                        child = toLegacyStringArrayPayload(payload, stringPayloadType);
                    } else if (!isStringList(payload)) {
                        child = toLegacyArrayPayload(payload, nbtPayloadType);
                    } else {
                        child = payload.copy();
                    }
                } else if (isAdditionalDataStringSet(key, child)) {
                    child = toLegacyStringArrayPayload((ListTag) child, stringPayloadType);
                }
                compound.put(key, normalizeTag(child, nbtPayloadType, stringPayloadType));
            }
        } else if (tag instanceof ListTag list) {
            for (int index = 0; index < list.size(); index++) {
                list.set(index, normalizeTag(list.get(index), nbtPayloadType, stringPayloadType));
            }
        }
        return tag;
    }

    /** LDLib 1's {@code Set<String>} accessor requires StringPayload entries wrapped as {@code {t,p}}. */
    private static boolean isAdditionalDataStringSet(String key, Tag value) {
        return "additionalData".equals(key) && value instanceof ListTag list && isStringList(list);
    }

    /** LDLib2's managed-list wrapper is identified by its exact {@code {uid, payload}} shape. */
    private static boolean isManagedListEnvelope(Tag tag) {
        if (!(tag instanceof CompoundTag envelope) || envelope.getAllKeys().size() != 2) {
            return false;
        }
        return envelope.contains("uid", Tag.TAG_INT) && envelope.contains("payload", Tag.TAG_LIST);
    }

    private static boolean isStringList(ListTag list) {
        return list.isEmpty() || list.getElementType() == Tag.TAG_STRING;
    }

    /** LDLib 2 persists JOML vectors as [x, y, z]; LDLib 1's managed accessor expects {x,y,z}. */
    private static boolean isVector3fField(String key, Tag value) {
        if (!VECTOR3F_FIELDS.contains(key) || !(value instanceof ListTag list) || list.size() != 3) {
            return false;
        }
        return list.getElementType() == Tag.TAG_FLOAT
                || list.getElementType() == Tag.TAG_DOUBLE
                || list.getElementType() == Tag.TAG_INT;
    }

    private static CompoundTag toLegacyVector3f(ListTag vector) {
        var legacyVector = new CompoundTag();
        legacyVector.putFloat("x", vector.getFloat(0));
        legacyVector.putFloat("y", vector.getFloat(1));
        legacyVector.putFloat("z", vector.getFloat(2));
        return legacyVector;
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

    private static ListTag toLegacyStringArrayPayload(ListTag payload, byte stringPayloadType) {
        var legacyPayload = new ListTag();
        for (var entry : payload) {
            if (entry instanceof CompoundTag compound
                    && compound.contains("t", Tag.TAG_BYTE)
                    && compound.contains("p")) {
                legacyPayload.add(entry.copy());
                continue;
            }
            var wrapped = new CompoundTag();
            wrapped.putByte("t", stringPayloadType);
            wrapped.put("p", entry.copy());
            legacyPayload.add(wrapped);
        }
        return legacyPayload;
    }
}
