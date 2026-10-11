package com.lowdragmc.photon.client.util;

import com.lowdragmc.photon.Photon;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.function.Supplier;

/** Keeps compatibility fallbacks while making persisted codec failures diagnosable. */
public final class PhotonCodecLog {
    private PhotonCodecLog() {
    }

    public static <T> T decodeOr(Codec<T> codec, Tag tag, String subject, Supplier<T> fallback) {
        return codec.parse(NbtOps.INSTANCE, tag)
                .resultOrPartial(error -> Photon.LOGGER.error("Failed to decode Photon {} from saved data: {}",
                        subject, error))
                .orElseGet(fallback);
    }

    public static <T> Tag encodeOrNull(Codec<T> codec, T value, String subject) {
        return codec.encodeStart(NbtOps.INSTANCE, value)
                .resultOrPartial(error -> Photon.LOGGER.error("Failed to encode Photon {} for saving: {}",
                        subject, error))
                .orElse(null);
    }
}
