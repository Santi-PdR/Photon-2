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

}


