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

    private static CompoundTag envelope(CompoundTag entry) {
        var payload = new ListTag();
        payload.add(entry);
        var envelope = new CompoundTag();
        envelope.putInt("uid", 1);
        envelope.put("payload", payload);
        return envelope;
    }
}
