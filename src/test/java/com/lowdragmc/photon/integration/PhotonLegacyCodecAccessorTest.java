package com.lowdragmc.photon.integration;

import com.lowdragmc.lowdraglib.syncdata.AccessorOp;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import com.mojang.serialization.Codec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PhotonLegacyCodecAccessorTest {
    @Test
    void genericCodecBridgeRoundTripsTheShieldMaterialPathTokenThroughNbt() {
        var path = "file(./ldlib2/assets/ldlib2/resources/global/shield.material.nbt)";
        var accessor = PhotonLDLibClientPlugin.legacyCodecAccessor(String.class, Codec.STRING);

        var payload = assertInstanceOf(NbtTagPayload.class, accessor.serialize(AccessorOp.PERSISTED, path));
        var tag = assertInstanceOf(net.minecraft.nbt.StringTag.class, payload.getPayload());
        assertEquals(path, tag.getAsString());
        assertEquals(path, accessor.deserialize(AccessorOp.PERSISTED, (ITypedPayload<?>) payload));
    }
}
