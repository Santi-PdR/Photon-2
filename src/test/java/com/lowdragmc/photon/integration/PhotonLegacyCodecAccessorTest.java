package com.lowdragmc.photon.integration;

import com.lowdragmc.lowdraglib.syncdata.AccessorOp;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.math.Range;
import com.mojang.serialization.Codec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

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
    void resolvesMissingEditorFileUnderLdlibAssetsRoot() throws Exception {
        var storedPath = "./ldlib2/assets/ldlib2/resources/global/shield.material.nbt";
        var assetsDir = tempDir.resolve("ldlib2/assets");
        var actualFile = assetsDir.resolve("ldlib2/assets/ldlib2/resources/global/shield.material.nbt");
        Files.createDirectories(actualFile.getParent());
        Files.createFile(actualFile);

        var resolved = PhotonLDLibClientPlugin.resolveEditorResourceFile(
                storedPath, tempDir.resolve(storedPath.substring(2)).toFile(), assetsDir);

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
