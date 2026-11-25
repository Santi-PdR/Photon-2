package com.lowdragmc.photon.client.util;

import com.lowdragmc.lowdraglib.syncdata.payload.ArrayPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

/** Handles persistence payloads shared by Photon and the Forge 1.20.1 LDLib readers. */
public final class LDLibPersistenceCompat {
    /** Refuse corrupt managed-list UIDs before they can allocate or loop over attacker-sized counts. */
    public static final int MAX_MANAGED_LIST_ENTRIES = 4096;

    private LDLibPersistenceCompat() {
    }

    public static int checkedManagedListCount(int count, String listName) {
        if (count < 0 || count > MAX_MANAGED_LIST_ENTRIES) {
            throw new IllegalArgumentException("Managed " + listName + " list count " + count
                    + " is outside the safe range 0.." + MAX_MANAGED_LIST_ENTRIES);
        }
        return count;
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

}


