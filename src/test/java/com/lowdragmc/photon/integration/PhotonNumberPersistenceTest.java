package com.lowdragmc.photon.integration;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PhotonNumberPersistenceTest {
    @Test
    void preservesNumericNbtTypesAndValues() {
        Number[] values = { (byte) 7, (short) 300, 42, 5_000_000_000L, 1.25F, 2.5D };
        byte[] tagTypes = { Tag.TAG_BYTE, Tag.TAG_SHORT, Tag.TAG_INT, Tag.TAG_LONG, Tag.TAG_FLOAT, Tag.TAG_DOUBLE };

        for (int i = 0; i < values.length; i++) {
            Tag encoded = PhotonLDLibClientPlugin.NUMBER_CODEC.encodeStart(NbtOps.INSTANCE, values[i])
                    .getOrThrow(false, message -> { throw new AssertionError(message); });
            Number decoded = PhotonLDLibClientPlugin.NUMBER_CODEC.parse(NbtOps.INSTANCE, encoded)
                    .getOrThrow(false, message -> { throw new AssertionError(message); });

            assertEquals(tagTypes[i], encoded.getId());
            assertInstanceOf(values[i].getClass(), decoded);
            assertEquals(values[i], decoded);
        }
    }
}
