package com.lowdragmc.photon.client.util;

import com.lowdragmc.lowdraglib.syncdata.TypedPayloadRegistries;
import com.lowdragmc.lowdraglib.syncdata.payload.ArrayPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LDLibPersistenceCompatTest {
    @Test
    void leavesTagsWithoutManagedListEnvelopesAlone() {
        var root = new CompoundTag();
        root.putString("ordinary", "value");

        assertSame(root, LDLibPersistenceCompat.normalize(root));
        assertEquals("value", root.getString("ordinary"));
    }

    @Test
    void unwrapsManagedListFieldsRecursivelyForLegacyLdlib() {
        var burst = new CompoundTag();
        burst.putInt("time", 12);
        var config = new CompoundTag();
        config.put("bursts", envelope(burst));
        config.put("materials", envelope(new CompoundTag()));
        config.put("influenceList", envelope(new CompoundTag()));
        config.put("emitters", envelope(new CompoundTag()));
        var fxObject = new CompoundTag();
        fxObject.put("config", config);
        var objects = new ListTag();
        objects.add(fxObject);
        var data = new CompoundTag();
        data.put("fxObjects", objects);
        var root = new CompoundTag();
        root.put("fxData", data);
        var unrelated = new CompoundTag();
        unrelated.put("payload", new ListTag());
        root.put("unrelated", unrelated);

        LDLibPersistenceCompat.normalize(root, (byte) 42);

        var normalizedConfig = root.getCompound("fxData").getList("fxObjects", 10)
                .getCompound(0).getCompound("config");
        for (var field : new String[]{"bursts", "materials", "influenceList", "emitters"}) {
            assertInstanceOf(ListTag.class, normalizedConfig.get(field), field);
            var element = ((ListTag) normalizedConfig.get(field)).getCompound(0);
            assertEquals(42, element.getByte("t"), field);
            assertInstanceOf(CompoundTag.class, element.get("p"), field);
        }
        var normalizedBurst = normalizedConfig.getList("bursts", 10).getCompound(0).getCompound("p");
        assertEquals(12, normalizedBurst.getInt("time"));
        assertInstanceOf(ListTag.class, root.getCompound("unrelated").get("payload"));
        assertSame(root, LDLibPersistenceCompat.normalize(root, (byte) 42));
    }

    @Test
    void legacyArrayPayloadDecodesShieldProjectBurstEnvelope() {
        var number = new CompoundTag();
        number.putFloat("number", 1.0f);
        var count = new CompoundTag();
        count.put("data", number);
        count.putString("type", "constant");
        var burst = new CompoundTag();
        burst.put("count", count);
        burst.putInt("cycles", 1);
        burst.putInt("interval", 1);
        burst.putFloat("probability", 1.0f);
        burst.putInt("time", 0);

        var config = new CompoundTag();
        config.put("bursts", envelope(burst));
        var fxObject = new CompoundTag();
        fxObject.put("config", config);
        var fxObjects = new ListTag();
        fxObjects.add(fxObject);
        var fxData = new CompoundTag();
        fxData.put("fxObjects", fxObjects);
        var root = new CompoundTag();
        root.put("fxData", fxData);
        byte nbtPayloadType = nbtPayloadType();
        LDLibPersistenceCompat.normalize(root, nbtPayloadType);

        var normalizedBurstList = root.getCompound("fxData").getList("fxObjects", ListTag.TAG_COMPOUND)
                .getCompound(0).getCompound("config").getList("bursts", ListTag.TAG_COMPOUND);
        var payload = new ArrayPayload();
        payload.deserializeNBT(normalizedBurstList);
        var decoded = (ITypedPayload<?>[]) payload.getPayload();
        assertEquals(1, decoded.length);
        var nbtPayload = assertInstanceOf(NbtTagPayload.class, decoded[0]);
        var decodedBurst = (CompoundTag) nbtPayload.getPayload();
        assertEquals(1.0f, decodedBurst.getCompound("count").getCompound("data").getFloat("number"));
        assertEquals("constant", decodedBurst.getCompound("count").getString("type"));
        assertEquals(1, decodedBurst.getInt("cycles"));
        assertEquals(1, decodedBurst.getInt("interval"));
        assertEquals(1.0f, decodedBurst.getFloat("probability"));
        assertEquals(0, decodedBurst.getInt("time"));
    }

    private static byte nbtPayloadType() {
        byte id = TypedPayloadRegistries.getId(NbtTagPayload.class);
        if (id < 0) {
            try {
                TypedPayloadRegistries.register(NbtTagPayload.class, NbtTagPayload::new, null, 1000);
            } catch (IllegalStateException registryAlreadyLoaded) {
                assumeTrue(false, "LDLib payload registry is already frozen without Photon client bootstrap");
            }
            id = TypedPayloadRegistries.getId(NbtTagPayload.class);
        }
        return id;
    }

    private static CompoundTag envelope(CompoundTag entry) {
        var payload = new ListTag();
        payload.add(entry);
        var envelope = new CompoundTag();
        envelope.putInt("uid", 1);
        envelope.put("payload", payload);
        return envelope;
    }
}
