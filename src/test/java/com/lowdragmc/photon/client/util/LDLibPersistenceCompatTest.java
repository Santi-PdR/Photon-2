package com.lowdragmc.photon.client.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class LDLibPersistenceCompatTest {
    @Test
    void unwrapsManagedListFieldsRecursivelyForLegacyLdlib() {
        var burst = new CompoundTag();
        burst.putInt("time", 12);
        var payload = new ListTag();
        payload.add(burst);
        var envelope = new CompoundTag();
        envelope.putInt("uid", 1);
        envelope.put("payload", payload);
        var config = new CompoundTag();
        config.put("bursts", envelope);
        var fxObject = new CompoundTag();
        fxObject.put("config", config);
        var objects = new ListTag();
        objects.add(fxObject);
        var data = new CompoundTag();
        data.put("fxObjects", objects);
        var root = new CompoundTag();
        root.put("fxData", data);
        var unrelated = new CompoundTag();
        unrelated.put("payload", payload.copy());
        root.put("unrelated", unrelated);

        LDLibPersistenceCompat.normalize(root);

        var normalizedBursts = root.getCompound("fxData").getList("fxObjects", 10)
                .getCompound(0).getCompound("config").get("bursts");
        assertInstanceOf(ListTag.class, normalizedBursts);
        assertEquals(12, ((ListTag) normalizedBursts).getCompound(0).getInt("time"));
        assertInstanceOf(ListTag.class, root.getCompound("unrelated").get("payload"));
        assertSame(root, LDLibPersistenceCompat.normalize(root));
    }
}
