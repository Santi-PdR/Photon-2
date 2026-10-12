package com.lowdragmc.photon.client.gameobject.emitter.data;

import com.lowdragmc.lowdraglib.syncdata.TypedPayloadRegistries;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import com.lowdragmc.photon.client.util.LDLibPersistenceCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererSettingPersistenceTest {
    @BeforeAll
    static void initializeNbtPayloadType() {
        if (TypedPayloadRegistries.getId(NbtTagPayload.class) < 0) {
            try {
                TypedPayloadRegistries.register(NbtTagPayload.class, NbtTagPayload::new, null, 1000);
            } catch (IllegalStateException registryAlreadyLoaded) {
                assumeTrue(false, "LDLib payload registry is already frozen without Photon client bootstrap");
            }
        }
    }

    @Test
    void rendererMaterialsRoundTripThroughLegacyArrayPayload() {
        var first = new CompoundTag();
        first.putBoolean("depthMask", true);
        var second = new CompoundTag();
        second.putBoolean("cull", false);

        ListTag encoded = LDLibPersistenceCompat.encodePayloadList(java.util.List.of(first, second));
        assertInstanceOf(ListTag.class, encoded);

        var decoded = LDLibPersistenceCompat.decodePayloadList(encoded);
        assertEquals(2, decoded.size());
        assertTrue(decoded.get(0).getBoolean("depthMask"));
        assertFalse(decoded.get(1).getBoolean("cull"));
    }
}
