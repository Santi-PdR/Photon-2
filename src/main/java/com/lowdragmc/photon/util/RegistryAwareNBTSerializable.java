package com.lowdragmc.photon.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

/** Photon-owned registry-aware NBT contract, independent of either LDLib serialization API. */
public interface RegistryAwareNBTSerializable<T extends Tag> {
    T serializeNBT(HolderLookup.Provider provider);

    void deserializeNBT(HolderLookup.Provider provider, T tag);
}
