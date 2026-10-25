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

/** Converts LDLib2 26.x persistence shapes to the Forge 1.20.1 reader format. */
public final class LDLibPersistenceCompat {
    private LDLibPersistenceCompat() {
    }

    public static ListTag encodePayloadList(List<CompoundTag> entries) {
        ITypedPayload<?>[] payloads = new ITypedPayload<?>[entries.size()];
        for (int i = 0; i < entries.size(); i++) payloads[i] = NbtTagPayload.of(entries.get(i));
        return (ListTag) ArrayPayload.of(payloads).serializeNBT();
    }

    public static List<CompoundTag> decodePayloadList(ListTag list) {
        ArrayPayload arrayPayload = new ArrayPayload();
        arrayPayload.deserializeNBT(list);
        List<CompoundTag> entries = new ArrayList<>();
        for (ITypedPayload<?> payload : arrayPayload.getPayload()) {
            Object value = payload.getPayload();
            if (!(value instanceof CompoundTag compound)) {
                throw new IllegalArgumentException("Managed list entry is not a compound tag: " + value);
            }
            entries.add(compound);
        }
        return entries;
    }

    public static CompoundTag normalize(CompoundTag root) {
        if (!containsCompatibilityData(root)) return root;

        byte nbtPayloadType = -1;
        if (containsManagedListEnvelope(root)) {
            nbtPayloadType = TypedPayloadRegistries.getId(NbtTagPayload.class);
            if (nbtPayloadType < 0) {
                throw new IllegalStateException("LDLib NBT payload type is not registered before Photon project load");
            }
        }
        byte stringPayloadType = -1;
        if (containsAdditionalDataStringSet(root)) {
            stringPayloadType = TypedPayloadRegistries.getId(StringPayload.class);
            if (stringPayloadType < 0) {
                throw new IllegalStateException("LDLib string payload type is not registered before Photon project load");
            }
        }
        normalizeTag(root, nbtPayloadType, stringPayloadType);
        return root;
    }

    private static boolean containsCompatibilityData(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (isManagedListEnvelope(child) || isAdditionalDataStringSet(key, child)
                        || containsCompatibilityData(child)) return true;
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) if (containsCompatibilityData(child)) return true;
        }
        return false;
    }

    private static boolean containsManagedListEnvelope(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (isManagedListEnvelope(child) || containsManagedListEnvelope(child)) return true;
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) if (containsManagedListEnvelope(child)) return true;
        }
        return false;
    }

    private static boolean containsAdditionalDataStringSet(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (isAdditionalDataStringSet(key, child)) return true;
                if (isManagedListEnvelope(child)
                        && isAdditionalDataStringSet(key, ((CompoundTag) child).get("payload"))) return true;
                if (containsAdditionalDataStringSet(child)) return true;
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) if (containsAdditionalDataStringSet(child)) return true;
        }
        return false;
    }

    private static Tag normalizeTag(Tag tag, byte nbtPayloadType, byte stringPayloadType) {
        if (tag instanceof CompoundTag compound) {
            for (String key : new ArrayList<>(compound.getAllKeys())) {
                Tag child = compound.get(key);
                if (isManagedListEnvelope(child)) {
                    ListTag payload = (ListTag) ((CompoundTag) child).get("payload");
                    child = isAdditionalDataStringSet(key, payload)
                            ? toLegacyStringArrayPayload(payload, stringPayloadType)
                            : !isStringList(payload) ? toLegacyArrayPayload(payload, nbtPayloadType) : payload.copy();
                } else if (isAdditionalDataStringSet(key, child)) {
                    child = toLegacyStringArrayPayload((ListTag) child, stringPayloadType);
                }
                compound.put(key, normalizeTag(child, nbtPayloadType, stringPayloadType));
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) list.set(i, normalizeTag(list.get(i), nbtPayloadType, stringPayloadType));
        }
        return tag;
    }

    private static boolean isAdditionalDataStringSet(String key, Tag value) {
        return "additionalData".equals(key) && value instanceof ListTag list && isStringList(list);
    }

    private static boolean isManagedListEnvelope(Tag tag) {
        return tag instanceof CompoundTag compound && compound.size() == 2
                && compound.contains("uid", Tag.TAG_INT) && compound.contains("payload", Tag.TAG_LIST);
    }

    private static boolean isStringList(ListTag list) {
        return list.isEmpty() || list.getElementType() == Tag.TAG_STRING;
    }

    private static ListTag toLegacyArrayPayload(ListTag payload, byte payloadType) {
        ListTag result = new ListTag();
        for (Tag entry : payload) {
            if (entry instanceof CompoundTag compound && compound.contains("t", Tag.TAG_BYTE)
                    && !compound.contains("p")) {
                result.add(entry.copy());
                continue;
            }
            CompoundTag wrapped = new CompoundTag();
            wrapped.putByte("t", payloadType);
            wrapped.put("p", entry.copy());
            result.add(wrapped);
        }
        return result;
    }

    private static ListTag toLegacyStringArrayPayload(ListTag payload, byte payloadType) {
        ListTag result = new ListTag();
        for (Tag entry : payload) {
            if (entry instanceof CompoundTag compound && compound.contains("t", Tag.TAG_BYTE)
                    && !compound.contains("p")) {
                result.add(entry.copy());
                continue;
            }
            CompoundTag wrapped = new CompoundTag();
            wrapped.putByte("t", payloadType);
            wrapped.put("p", entry.copy());
            result.add(wrapped);
        }
        return result;
    }
}
