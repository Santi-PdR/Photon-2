package com.lowdragmc.photon.client.gameobject.emitter.data.number.color;

import com.lowdragmc.lowdraglib2.registry.AutoRegistry;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.lowdraglib.syncdata.TypedPayloadRegistries;
import com.lowdragmc.lowdraglib.syncdata.accessor.PrimitiveAccessor;
import com.lowdragmc.lowdraglib.syncdata.payload.PrimitiveTypedPayload;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.util.PersistedCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.joml.Vector4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class HDRColorPersistenceTest {
    @BeforeAll
    static void initializeNumberFunctionCodecRegistry() {
        TypedPayloadRegistries.register(PrimitiveTypedPayload.FloatPayload.class,
                PrimitiveTypedPayload.FloatPayload::new, new PrimitiveAccessor.FloatAccessor(), 0);
        TypedPayloadRegistries.postInit();
        if (PhotonRegistries.NUMBER_FUNCTIONS == null) {
            PhotonRegistries.NUMBER_FUNCTIONS = AutoRegistry.LDLibRegisterClient.create(
                    Photon.id("number_function"), NumberFunction.class, AutoRegistry::noArgsCreator);
        }
    }

    @Test
    void persistedCodecRoundTripsAlphaAndHdrIntensity() {
        var codec = PersistedCodec.createCodec(HDRConstantColor::new);
        var original = new HDRConstantColor(new HDRColor(0.2f, 0.4f, 0.6f, 0.35f, 3f));

        var tag = (CompoundTag) codec.encodeStart(NbtOps.INSTANCE, original).result().orElseThrow();
        var restored = codec.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
        var color = restored.getColor();

        assertEquals(0.2f, color.getR(), 1.0e-6f);
        assertEquals(0.4f, color.getG(), 1.0e-6f);
        assertEquals(0.6f, color.getB(), 1.0e-6f);
        assertEquals(0.35f, color.getA(), 1.0e-6f);
        assertEquals(3f, color.getIntensity(), 1.0e-6f);
    }

    @Test
    void oldPersistedVectorWithoutAlphaDefaultsToOpaque() {
        var codec = PersistedCodec.createCodec(HDRConstantColor::new);
        var oldColor = new CompoundTag();
        oldColor.putFloat("x", 0.2f);
        oldColor.putFloat("y", 0.4f);
        oldColor.putFloat("z", 0.6f);
        oldColor.putFloat("w", 3f);
        var oldTag = new CompoundTag();
        oldTag.put("color", oldColor);

        var restored = codec.parse(NbtOps.INSTANCE, oldTag).result().orElseThrow();

        assertEquals(0.2f, restored.getColor().getR(), 1.0e-6f);
        assertEquals(0.4f, restored.getColor().getG(), 1.0e-6f);
        assertEquals(0.6f, restored.getColor().getB(), 1.0e-6f);
        assertEquals(1f, restored.getColor().getA(), 1.0e-6f);
        assertEquals(3f, restored.getColor().getIntensity(), 1.0e-6f);
    }

    @Test
    void randomColorPersistsBothHdrEndpoints() {
        var codec = PersistedCodec.createCodec(HDRRandomColor::new);
        var original = new HDRRandomColor(
                new HDRColor(0.1f, 0.2f, 0.3f, 0.4f, 2f),
                new HDRColor(0.5f, 0.6f, 0.7f, 0.8f, 4f));
        var colorA = original.getColorA();
        var colorB = original.getColorB();
        assertSame(colorA, original.getColorA());
        assertSame(colorB, original.getColorB());
        colorA.set(0.15f, 0.25f, 0.35f, 0.45f, 2.5f);
        colorB.set(0.55f, 0.65f, 0.75f, 0.85f, 4.5f);

        var sample = new Vector4f();
        original.sampleHDR(0f, () -> 0.5f, sample);
        assertEquals(1.425f, sample.x, 1.0e-6f);
        assertEquals(1.775f, sample.y, 1.0e-6f);
        assertEquals(2.125f, sample.z, 1.0e-6f);
        assertEquals(0.65f, sample.w, 1.0e-6f);

        var tag = (CompoundTag) codec.encodeStart(NbtOps.INSTANCE, original).result().orElseThrow();
        var restored = codec.parse(NbtOps.INSTANCE, tag).result().orElseThrow();

        assertColor(restored.getColorA(), 0.15f, 0.25f, 0.35f, 0.45f, 2.5f);
        assertColor(restored.getColorB(), 0.55f, 0.65f, 0.75f, 0.85f, 4.5f);
    }

    @Test
    void gettersKeepTheReferenceMutableColorContractAndPersistMutations() {
        var codec = PersistedCodec.createCodec(HDRConstantColor::new);
        var original = new HDRConstantColor(new HDRColor(0.1f, 0.2f, 0.3f, 0.4f, 1f));
        var exposedColor = original.getColor();
        assertSame(exposedColor, original.getColor());
        exposedColor.set(0.4f, 0.5f, 0.6f, 0.25f, 2f);

        var sample = new Vector4f();
        original.sampleHDR(0f, () -> 0f, sample);
        assertEquals(0.8f, sample.x, 1.0e-6f);
        assertEquals(1f, sample.y, 1.0e-6f);
        assertEquals(1.2f, sample.z, 1.0e-6f);
        assertEquals(0.25f, sample.w, 1.0e-6f);

        var tag = (CompoundTag) codec.encodeStart(NbtOps.INSTANCE, original).result().orElseThrow();
        var restored = codec.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
        assertColor(restored.getColor(), 0.4f, 0.5f, 0.6f, 0.25f, 2f);
    }

    @Test
    void randomColorMigratesLegacyVectorEndpoints() {
        var codec = PersistedCodec.createCodec(HDRRandomColor::new);
        var tag = new CompoundTag();
        tag.put("colorA", vectorTag(0.1f, 0.2f, 0.3f, 2f));
        tag.put("colorB", vectorTag(0.5f, 0.6f, 0.7f, 4f));

        var restored = codec.parse(NbtOps.INSTANCE, tag).result().orElseThrow();

        assertColor(restored.getColorA(), 0.1f, 0.2f, 0.3f, 1f, 2f);
        assertColor(restored.getColorB(), 0.5f, 0.6f, 0.7f, 1f, 4f);
    }

    private static CompoundTag vectorTag(float x, float y, float z, float w) {
        var tag = new CompoundTag();
        tag.putFloat("x", x);
        tag.putFloat("y", y);
        tag.putFloat("z", z);
        tag.putFloat("w", w);
        return tag;
    }

    private static void assertColor(HDRColor color, float red, float green, float blue,
                                    float alpha, float intensity) {
        assertEquals(red, color.getR(), 1.0e-6f);
        assertEquals(green, color.getG(), 1.0e-6f);
        assertEquals(blue, color.getB(), 1.0e-6f);
        assertEquals(alpha, color.getA(), 1.0e-6f);
        assertEquals(intensity, color.getIntensity(), 1.0e-6f);
    }
}
