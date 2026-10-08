package com.lowdragmc.photon.util;

import com.lowdragmc.lowdraglib.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.Platform;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.function.Supplier;

/** Codec adapter that stores LDLib 1 {@link IPersistedSerializable} objects as their native NBT. */
public final class PersistedCodec {
    private PersistedCodec() {
    }

    public static <T extends IPersistedSerializable> Codec<T> createCodec(Supplier<T> factory) {
        return Codec.PASSTHROUGH.flatXmap(dynamic -> {
            Tag tag = dynamic.convert(NbtOps.INSTANCE).getValue();
            if (!(tag instanceof CompoundTag compound)) {
                return DataResult.error(() -> "Expected compound NBT for persisted Photon value");
            }
            T value = factory.get();
            if (value instanceof RegistryAwareNBTSerializable<?> registryAware) {
                @SuppressWarnings("unchecked")
                RegistryAwareNBTSerializable<CompoundTag> serializable =
                        (RegistryAwareNBTSerializable<CompoundTag>) registryAware;
                serializable.deserializeNBT(Platform.getFrozenRegistry(), compound);
            } else {
                value.deserializeNBT(compound);
            }
            return DataResult.success(value);
        }, value -> {
            Tag tag;
            if (value instanceof RegistryAwareNBTSerializable<?> registryAware) {
                @SuppressWarnings("unchecked")
                RegistryAwareNBTSerializable<CompoundTag> serializable =
                        (RegistryAwareNBTSerializable<CompoundTag>) registryAware;
                tag = serializable.serializeNBT(Platform.getFrozenRegistry());
            } else {
                tag = value.serializeNBT();
            }
            return DataResult.success(new Dynamic<>(NbtOps.INSTANCE, tag));
        });
    }
}
