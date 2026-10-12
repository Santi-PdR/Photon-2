package com.lowdragmc.photon.client.util;

import com.lowdragmc.lowdraglib.syncdata.TypedPayloadRegistries;
import com.lowdragmc.lowdraglib.syncdata.payload.ArrayPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import com.lowdragmc.lowdraglib.syncdata.accessor.Vector3fAccessor;
import com.lowdragmc.lowdraglib2.registry.AutoRegistry;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.gameobject.emitter.data.ExternalForcesSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.fixer.PhotonFXProjectDataFixer;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LDLibPersistenceCompatTest {
    @BeforeAll
    static void initializeNumberFunctionCodecRegistry() {
        if (PhotonRegistries.NUMBER_FUNCTIONS == null) {
            PhotonRegistries.NUMBER_FUNCTIONS = AutoRegistry.LDLibRegisterClient.create(
                    Photon.id("number_function"), NumberFunction.class, AutoRegistry::noArgsCreator);
        }
    }

    @Test
    void leavesTagsWithoutManagedListEnvelopesAlone() {
        var root = new CompoundTag();
        root.putString("ordinary", "value");

        assertSame(root, LDLibPersistenceCompat.normalize(root));
        assertEquals("value", root.getString("ordinary"));
    }

    @Test
    void migratesLdlib2VectorListsForLegacyLdlibManagedAccessors() {
        var modelPivot = new ListTag();
        modelPivot.add(FloatTag.valueOf(0.25f));
        modelPivot.add(FloatTag.valueOf(0.5f));
        modelPivot.add(FloatTag.valueOf(-0.75f));
        var renderer = new CompoundTag();
        renderer.put("modelPivot", modelPivot);

        // LDLib2 scene transforms use their own list format and must remain untouched.
        var localPosition = new ListTag();
        localPosition.add(FloatTag.valueOf(1));
        localPosition.add(FloatTag.valueOf(2));
        localPosition.add(FloatTag.valueOf(3));
        var transform = new CompoundTag();
        transform.put("localPosition", localPosition);

        var root = new CompoundTag();
        root.put("renderer", renderer);
        root.put("transform", transform);

        LDLibPersistenceCompat.normalize(root);

        var normalizedPivot = renderer.getCompound("modelPivot");
        assertEquals(0.25f, normalizedPivot.getFloat("x"));
        assertEquals(0.5f, normalizedPivot.getFloat("y"));
        assertEquals(-0.75f, normalizedPivot.getFloat("z"));
        assertInstanceOf(ListTag.class, transform.get("localPosition"));
        assertEquals(3, transform.getList("localPosition", ListTag.TAG_FLOAT).size());

        var vector = new Vector3fAccessor().deserialize(null, NbtTagPayload.of(normalizedPivot));
        assertEquals(0.25f, vector.x);
        assertEquals(0.5f, vector.y);
        assertEquals(-0.75f, vector.z);
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
        // Real Photon/LDLib data can contain managed arrays whose field names vary by setting.
        config.put("customManagedArray", envelope(new CompoundTag()));
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
        var domainPayload = new CompoundTag();
        domainPayload.putInt("uid", 7);
        domainPayload.put("payload", new ListTag());
        domainPayload.putString("kind", "domain-data");
        root.put("domainPayload", domainPayload);

        LDLibPersistenceCompat.normalize(root, (byte) 42);

        var normalizedConfig = root.getCompound("fxData").getList("fxObjects", 10)
                .getCompound(0).getCompound("config");
        for (var field : new String[]{"bursts", "materials", "influenceList", "emitters", "customManagedArray"}) {
            assertInstanceOf(ListTag.class, normalizedConfig.get(field), field);
            var element = ((ListTag) normalizedConfig.get(field)).getCompound(0);
            assertEquals(42, element.getByte("t"), field);
            assertInstanceOf(CompoundTag.class, element.get("p"), field);
        }
        var normalizedBurst = normalizedConfig.getList("bursts", 10).getCompound(0).getCompound("p");
        assertEquals(12, normalizedBurst.getInt("time"));
        assertInstanceOf(ListTag.class, root.getCompound("unrelated").get("payload"));
        assertEquals("domain-data", root.getCompound("domainPayload").getString("kind"));
        assertInstanceOf(ListTag.class, root.getCompound("domainPayload").get("payload"));
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
        nbtPayloadType();
        LDLibPersistenceCompat.normalize(root);

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

    @Test
    void preservesStringEntriesForCustomReadOnlyManagedListDeserializers() {
        var expectedRef = "a3d8bb14-3241-4810-9053-9e364c7a005b";
        var refs = new ListTag();
        refs.add(StringTag.valueOf(expectedRef));
        var config = new CompoundTag();
        config.put("influenceList", envelope(refs));

        LDLibPersistenceCompat.normalize(config, (byte) 42);

        var normalized = config.getList("influenceList", ListTag.TAG_STRING);
        assertEquals(1, normalized.size());
        assertEquals(expectedRef, normalized.getString(0));

        var setting = new ExternalForcesSetting();
        setting.deserializeNBT(config);
        assertEquals(expectedRef, setting.getInfluenceList().get(0).getTransformId().toString());
    }

    @Test
    void migratesAndLoadsManagedListsAtRealShieldProjectPaths() {
        var material = new CompoundTag();
        material.putString("type", "sprite");
        var burst = new CompoundTag();
        burst.putInt("time", 0);

        var renderer = new CompoundTag();
        renderer.put("materials", envelope(material));
        var emission = new CompoundTag();
        emission.put("bursts", envelope(burst));

        var araRenderer = new CompoundTag();
        araRenderer.put("materials", envelope(material));
        var araConfig = new CompoundTag();
        araConfig.put("renderer", araRenderer);
        var trailRenderer = new CompoundTag();
        trailRenderer.put("materials", envelope(material));
        var trailConfig = new CompoundTag();
        trailConfig.put("renderer", trailRenderer);
        var trails = new CompoundTag();
        trails.put("araConfig", araConfig);
        trails.put("config", trailConfig);

        var config = new CompoundTag();
        config.put("renderer", renderer);
        config.put("emission", emission);
        config.put("trails", trails);
        var fxObjectData = new CompoundTag();
        fxObjectData.put("config", config);
        var fxObject = new CompoundTag();
        fxObject.put("data", fxObjectData);
        var fxObjects = new ListTag();
        fxObjects.add(fxObject);
        var fxData = new CompoundTag();
        fxData.put("fxObjects", fxObjects);
        var fx = new CompoundTag();
        fx.put("fxData", fxData);
        var projectData = new CompoundTag();
        projectData.put("fx", fx);

        // shield.fxproj declares schema v3; load follows the same data-fixer then compatibility
        // normalization order as FXProject.deserializeNBT before LDLib 1's persisted parser runs.
        var migrated = PhotonFXProjectDataFixer.INSTANCE.applyFixes(3, 5, projectData);
        nbtPayloadType();
        LDLibPersistenceCompat.normalize(migrated);

        var loadedConfig = migrated.getCompound("fx").getCompound("fxData")
                .getList("fxObjects", ListTag.TAG_COMPOUND).getCompound(0)
                .getCompound("data").getCompound("config");
        assertLegacyArrayPayload(loadedConfig.getCompound("emission").getList("bursts", ListTag.TAG_COMPOUND),
                "time", 0);
        assertLegacyArrayPayload(loadedConfig.getCompound("renderer").getList("materials", ListTag.TAG_COMPOUND),
                "type", "sprite");
        assertLegacyArrayPayload(loadedConfig.getCompound("trails").getCompound("araConfig")
                        .getCompound("renderer").getList("materials", ListTag.TAG_COMPOUND),
                "type", "sprite");
        assertLegacyArrayPayload(loadedConfig.getCompound("trails").getCompound("config")
                        .getCompound("renderer").getList("materials", ListTag.TAG_COMPOUND),
                "type", "sprite");
    }

    private static void assertLegacyArrayPayload(ListTag list, String valueKey, int expected) {
        var decoded = decodeLegacyArray(list);
        assertEquals(expected, ((CompoundTag) decoded.getPayload()).getInt(valueKey));
    }

    private static void assertLegacyArrayPayload(ListTag list, String valueKey, String expected) {
        var decoded = decodeLegacyArray(list);
        assertEquals(expected, ((CompoundTag) decoded.getPayload()).getString(valueKey));
    }

    private static NbtTagPayload decodeLegacyArray(ListTag list) {
        var payload = new ArrayPayload();
        payload.deserializeNBT(list);
        var decoded = (ITypedPayload<?>[]) payload.getPayload();
        assertEquals(1, decoded.length);
        return assertInstanceOf(NbtTagPayload.class, decoded[0]);
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
        return envelope(payload);
    }

    private static CompoundTag envelope(ListTag payload) {
        var envelope = new CompoundTag();
        envelope.putInt("uid", 1);
        envelope.put("payload", payload);
        return envelope;
    }
}
