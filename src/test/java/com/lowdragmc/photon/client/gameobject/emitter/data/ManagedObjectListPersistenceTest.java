package com.lowdragmc.photon.client.gameobject.emitter.data;

import com.lowdragmc.lowdraglib.syncdata.TypedPayloadRegistries;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import com.lowdragmc.lowdraglib2.registry.AutoRegistry;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.util.LDLibPersistenceCompat;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ManagedObjectListPersistenceTest {
    @BeforeAll
    static void initializeNbtPayloadType() {
        if (PhotonRegistries.NUMBER_FUNCTIONS == null) {
            PhotonRegistries.NUMBER_FUNCTIONS = AutoRegistry.LDLibRegisterClient.create(
                    Photon.id("number_function"), com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction.class,
                    AutoRegistry::noArgsCreator);
        }
        if (TypedPayloadRegistries.getId(NbtTagPayload.class) < 0) {
            try {
                TypedPayloadRegistries.register(NbtTagPayload.class, NbtTagPayload::new, null, 1000);
            } catch (IllegalStateException registryAlreadyLoaded) {
                assumeTrue(false, "LDLib payload registry is already frozen without Photon client bootstrap");
            }
        }
    }

    @Test
    void emissionBurstsDecodeWithoutLdlibCollectionAccessor() {
        var tag = new CompoundTag();
        tag.put("bursts", LDLibPersistenceCompat.encodePayloadList(List.of(new CompoundTag())));
        var restored = new EmissionSetting();
        restored.deserializeNBT(tag);

        assertEquals(1, restored.getBursts().size());
    }

    @Test
    void subEmittersDecodeWithoutLdlibCollectionAccessor() {
        var tag = new CompoundTag();
        tag.put("emitters", LDLibPersistenceCompat.encodePayloadList(List.of(new CompoundTag())));
        var restored = new SubEmittersSetting();
        restored.deserializeNBT(tag);

        assertEquals(1, restored.getEmitters().size());
    }
}
