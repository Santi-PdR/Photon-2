package com.lowdragmc.photon.integration;

import com.lowdragmc.lowdraglib.syncdata.AccessorOp;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.math.Range;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves;
import com.mojang.serialization.Codec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Vector2f;
import org.joml.Vector2i;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PhotonLegacyCodecAccessorTest {
    @TempDir
    Path tempDir;

    @Test
    void genericCodecBridgeRoundTripsTheShieldMaterialPathTokenThroughNbt() {
        var path = "file(./ldlib2/assets/ldlib2/resources/global/shield.material.nbt)";
        var accessor = PhotonLDLibClientPlugin.legacyCodecAccessor(String.class, Codec.STRING);

        var payload = assertInstanceOf(NbtTagPayload.class, accessor.serialize(AccessorOp.PERSISTED, path));
        var tag = assertInstanceOf(net.minecraft.nbt.StringTag.class, payload.getPayload());
        assertEquals(path, tag.getAsString());
        assertEquals(path, accessor.deserialize(AccessorOp.PERSISTED, (ITypedPayload<?>) payload));
    }

    @Test
    void rangeCodecBridgeRoundTripsPhotonSpeedRangesThroughLegacyLdlibPersistence() {
        var range = Range.of(2.5f, 8.5f);
        var accessor = PhotonLDLibClientPlugin.legacyCodecAccessor(Range.class, Range.CODEC);

        var payload = assertInstanceOf(NbtTagPayload.class, accessor.serialize(AccessorOp.PERSISTED, range));
        var tag = assertInstanceOf(net.minecraft.nbt.CompoundTag.class, payload.getPayload());
        assertEquals(2.5f, tag.getFloat("a"));
        assertEquals(8.5f, tag.getFloat("b"));

        var restored = accessor.deserialize(AccessorOp.PERSISTED, (ITypedPayload<?>) payload);
        assertEquals(2.5f, restored.getA().floatValue());
        assertEquals(8.5f, restored.getB().floatValue());
    }

    @Test
    void gradientColorBridgeRoundTripsPhotonGradientFunctionsThroughLegacyLdlibPersistence() {
        var gradient = new GradientColor(0xFFFF0000, 0xFF0000FF);
        gradient.addAlpha(0.25f, 0.4f);
        gradient.addAlpha(0.75f, 0.9f);
        var accessor = PhotonLDLibClientPlugin.legacyGradientColorAccessor();

        var payload = assertInstanceOf(NbtTagPayload.class, accessor.serialize(AccessorOp.PERSISTED, gradient));
        var tag = assertInstanceOf(net.minecraft.nbt.CompoundTag.class, payload.getPayload());
        assertEquals(gradient, accessor.deserialize(AccessorOp.PERSISTED, (ITypedPayload<?>) payload));
    }

    @Test
    void ecbCurveCodecBridgeRoundTripsCurveControlPointsThroughLegacyLdlibPersistence() {
        var curves = new ECBCurves(0, 0, 0.2f, 0.1f, 0.8f, 0.9f, 1, 1);
        var accessor = PhotonLDLibClientPlugin.legacyCodecAccessor(ECBCurves.class, ECBCurves.CODEC);

        var payload = assertInstanceOf(NbtTagPayload.class, accessor.serialize(AccessorOp.PERSISTED, curves));
        assertInstanceOf(net.minecraft.nbt.ListTag.class, payload.getPayload());
        var restored = accessor.deserialize(AccessorOp.PERSISTED, (ITypedPayload<?>) payload);
        for (float x : new float[]{0.1f, 0.25f, 0.5f, 0.75f, 0.9f}) {
            assertEquals(curves.getCurveY(x), restored.getCurveY(x), 0.0001f);
        }
    }

    @Test
    void jomlVector2CodecsRoundTripPersistedVectorValues() {
        var vector2fAccessor = PhotonLDLibClientPlugin.legacyCodecAccessor(
                Vector2f.class, PhotonLDLibClientPlugin.VECTOR2F_CODEC);
        var vector2fPayload = vector2fAccessor.serialize(AccessorOp.PERSISTED, new Vector2f(0.25f, -0.75f));
        var vector2f = vector2fAccessor.deserialize(AccessorOp.PERSISTED, vector2fPayload);
        assertEquals(0.25f, vector2f.x, 0.0001f);
        assertEquals(-0.75f, vector2f.y, 0.0001f);

        var vector2iAccessor = PhotonLDLibClientPlugin.legacyCodecAccessor(
                Vector2i.class, PhotonLDLibClientPlugin.VECTOR2I_CODEC);
        var vector2iPayload = vector2iAccessor.serialize(AccessorOp.PERSISTED, new Vector2i(4, 7));
        var vector2i = vector2iAccessor.deserialize(AccessorOp.PERSISTED, vector2iPayload);
        assertEquals(4, vector2i.x);
        assertEquals(7, vector2i.y);
    }

    @Test
    void resolvesMissingEditorFileUnderLdlibAssetsRoot() throws Exception {
        var storedPath = "./ldlib2/assets/ldlib2/resources/global/shield.material.nbt";
        var assetsDir = tempDir.resolve("ldlib2/assets");
        var actualFile = assetsDir.resolve("ldlib2/resources/global/shield.material.nbt");
        Files.createDirectories(actualFile.getParent());
        Files.createFile(actualFile);

        var resolved = PhotonLDLibClientPlugin.resolveEditorResourceFile(
                storedPath, tempDir.resolve("missing/original/path/shield.material.nbt").toFile(), assetsDir);

        assertEquals(actualFile.toFile(), resolved);
    }

    @Test
    void keepsExistingAndUnrelatedEditorPathsUnchanged() throws Exception {
        var assetsDir = tempDir.resolve("ldlib2/assets");
        var existing = tempDir.resolve("ldlib2/assets/ldlib2/resources/global/shield.material.nbt");
        Files.createDirectories(existing.getParent());
        Files.createFile(existing);

        assertEquals(existing.toFile(), PhotonLDLibClientPlugin.resolveEditorResourceFile(
                "./ldlib2/assets/ldlib2/resources/global/shield.material.nbt", existing.toFile(), assetsDir));
        var unrelated = tempDir.resolve("other/shield.material.nbt").toFile();
        assertEquals(unrelated, PhotonLDLibClientPlugin.resolveEditorResourceFile(
                "./other/shield.material.nbt", unrelated, assetsDir));
    }
}
