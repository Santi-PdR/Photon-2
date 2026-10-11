package com.lowdragmc.photon.client.util;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.IntTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhotonCodecLogTest {
    @Test
    void keepsTheCallerFallbackWhenSavedDataCannotBeDecoded() {
        assertEquals("fallback", PhotonCodecLog.decodeOr(
                Codec.STRING, IntTag.valueOf(7), "test value", () -> "fallback"));
    }

    @Test
    void usesPartialCodecResultsInsteadOfDiscardingRecoverableData() {
        var partialCodec = Codec.STRING.flatXmap(
                value -> value.isBlank()
                        ? com.mojang.serialization.DataResult.error(() -> "blank value", "recovered")
                        : com.mojang.serialization.DataResult.success(value),
                com.mojang.serialization.DataResult::success);
        assertEquals("recovered", PhotonCodecLog.decodeOr(
                partialCodec, net.minecraft.nbt.StringTag.valueOf(""), "test value", () -> "fallback"));
    }
}
