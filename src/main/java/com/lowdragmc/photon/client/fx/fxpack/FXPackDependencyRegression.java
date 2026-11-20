package com.lowdragmc.photon.client.fx.fxpack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exercises the same dependency traversal that runs after each fxpack rewrite. */
public final class FXPackDependencyRegression {
    private FXPackDependencyRegression() {}

    public static void verify() throws Exception {
        verifySyncPacketTokens();
        verifyEffectCommandCodecBounds();
        verifyUnsafeEntryNameParity();
        var traversalId = FXPackExporter.fxId("../unsafe", "../../../../pack.mcmeta");
        var rawTraversalId = new net.minecraft.resources.ResourceLocation("test", "../../pack.mcmeta");
        var fxDirectory = Path.of("assets").resolve(traversalId.getNamespace()).resolve("fx").normalize();
        var fxEntry = fxDirectory.resolve(traversalId.getPath() + ".fx").normalize();
        if (traversalId.getNamespace().contains("/") || traversalId.getNamespace().equals(".")
                || traversalId.getNamespace().equals("..") || !fxEntry.startsWith(fxDirectory)
                || FXPacks.isSafePackLocation(rawTraversalId)
                || !FXPacks.isSafePackLocation(FXPackExporter.fxId("Example-Pack", "spells/fire blast"))
                || !FXPackExporter.fxId("Example-Pack", "spells/fire blast").toString()
                .equals("example-pack:spells/fire_blast")) {
            throw new IllegalStateException("FX pack export ids were not safely sanitized: " + traversalId);
        }
        var json = "{\"buffers\":[{\"uri\":\"../buffers/mesh.bin\"}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int padded = (json.length + 3) & ~3;
        var glb = java.nio.ByteBuffer.allocate(20 + padded).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        glb.putInt(0x46546c67).putInt(2).putInt(20 + padded).putInt(padded).putInt(0x4e4f534a).put(json);
        while (glb.hasRemaining()) glb.put((byte) ' ');
        var glbRefs = GltfAssetReferences.buffers(new net.minecraft.resources.ResourceLocation("test:models/foo/anim.glb"), glb.array());
        if (!glbRefs.equals(java.util.List.of(new net.minecraft.resources.ResourceLocation("test:models/buffers/mesh.bin"))))
            throw new IllegalStateException("GLB external buffer reference was not resolved: " + glbRefs);
        var directory = Files.createTempDirectory("photon-pack-deps-");
        var archive = directory.resolve("test.fxpack");
        var unsafeArchive = directory.resolve("unsafe.fxpack");
        try {
            try (var zip = FileSystems.newFileSystem(java.net.URI.create("jar:" + archive.toUri()), Map.of("create", "true"))) {
                boolean rejectedTraversal = false;
                try {
                    FXPacks.entryPath(zip, rawTraversalId);
                } catch (IllegalArgumentException expected) {
                    rejectedTraversal = true;
                }
                if (!rejectedTraversal) throw new IllegalStateException("FX pack path accepted a traversal resource id");
                var fx = zip.getPath("assets/test/fx/effect.fx");
                Files.createDirectories(fx.getParent());
                var root = new CompoundTag();
                var model = new CompoundTag();
                var data = new CompoundTag();
                data.putString("modelLocation", "test:block/shield");
                model.putString("type", "json_model");
                model.put("data", data);
                root.put("model", model);
                var shader = new CompoundTag();
                var shaderData = new CompoundTag();
                shader.putString("type", "custom_shader");
                shaderData.putString("shaderLocation", "test:ci/example");
                shader.put("data", shaderData);
                root.put("shader", shader);
                root.putString("mesh", "test:models/foo/anim.gltf");
                try (var out = Files.newOutputStream(fx)) { NbtIo.writeCompressed(root, out); }
                put(zip.getPath("assets/test/models/block/shield.json"),
                        "{\"model\":\"test:models/shield.obj\",\"textures\":{\"base\":\"test:item/pattern\"}}");
                put(zip.getPath("assets/test/models/shield.obj"), "v 0 0 0\n");
                put(zip.getPath("assets/test/textures/item/pattern.png"), "texture");
                put(zip.getPath("assets/test/textures/item/pattern.png.mcmeta"), "metadata");
                put(zip.getPath("assets/test/models/foo/anim.gltf"), "{\"buffers\":[{\"uri\":\"../buffers/mesh.bin\"}]}");
                put(zip.getPath("assets/test/models/buffers/mesh.bin"), "buffer");
                put(zip.getPath("assets/test/shaders/core/ci/example.json"),
                        "{\"vertex\":\"test:ci/example\",\"fragment\":\"test:ci/example\"}");
                put(zip.getPath("assets/test/shaders/core/ci/example.vsh"),
                        "#moj_import \"test:relative.glsl\"\n#moj_import \"test:../parent.glsl\"\n#moj_import <test:shared.glsl>\n");
                put(zip.getPath("assets/test/shaders/core/ci/example.fsh"), "// fragment\n");
                put(zip.getPath("assets/test/shaders/core/ci/relative.glsl"), "// relative\n");
                put(zip.getPath("assets/test/shaders/core/parent.glsl"), "// parent\n");
                put(zip.getPath("assets/test/shaders/include/shared.glsl"), "// shared\n");
                put(zip.getPath("assets/test/unused.bin"), "orphan");
                FXPacks.gc(zip);
                for (var path : java.util.List.of("assets/test/models/block/shield.json", "assets/test/models/shield.obj",
                        "assets/test/textures/item/pattern.png", "assets/test/textures/item/pattern.png.mcmeta",
                        "assets/test/models/foo/anim.gltf", "assets/test/models/buffers/mesh.bin",
                        "assets/test/shaders/core/ci/relative.glsl", "assets/test/shaders/core/parent.glsl",
                        "assets/test/shaders/include/shared.glsl")) {
                    if (!Files.isRegularFile(zip.getPath(path))) throw new IllegalStateException("FX pack GC removed " + path);
                }
                if (Files.exists(zip.getPath("assets/test/unused.bin")))
                    throw new IllegalStateException("FX pack GC retained an unrelated file");
                var shaderJson = zip.getPath("assets/test/shaders/core/ci/example.json");
                put(zip.getPath("assets/test/unused.bin"), "keep if marking fails");
                Files.writeString(shaderJson, "{malformed shader json");
                FXPacks.gc(zip);
                if (!Files.exists(zip.getPath("assets/test/unused.bin"))) {
                    throw new IllegalStateException("FX pack GC swept files after shader dependency tracing failed");
                }
                // Simulate a hand-built pack entry with an invalid resource path. Listing should skip it,
                // not throw while opening the pack contents dialog.
                put(zip.getPath("assets/test/fx/bad name.fx"), "not an effect");
            }
            var listed = FXPacks.listFx(archive.toFile());
            if (!listed.equals(java.util.List.of(new net.minecraft.resources.ResourceLocation("test", "effect")))) {
                throw new IllegalStateException("FX pack listing did not filter invalid resource ids: " + listed);
            }
            var expandedBytes = com.lowdragmc.photon.network.FxPackSync.expandedFxPackBytes(archive,
                    com.lowdragmc.photon.network.FxPackSync.MAX_EXPANDED_PACK_BYTES);
            if (expandedBytes < 0 || !com.lowdragmc.photon.network.FxPackSync.isValidFxPack(archive)) {
                throw new IllegalStateException("A valid generated FX pack failed streaming validation");
            }
            if (expandedBytes > 0 && com.lowdragmc.photon.network.FxPackSync.expandedFxPackBytes(
                    archive, expandedBytes - 1) >= 0) {
                throw new IllegalStateException("FX pack validation exceeded its caller-supplied expansion bound");
            }
            try (var zip = new ZipOutputStream(Files.newOutputStream(unsafeArchive))) {
                zip.putNextEntry(new ZipEntry("assets/test/fx/effect.fx"));
                zip.write(new byte[]{1, 2, 3});
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("assets/test/../../outside.txt"));
                zip.write(new byte[]{4});
                zip.closeEntry();
            }
            var unsafeValidation = com.lowdragmc.photon.network.FxPackSync.inspectFxPack(unsafeArchive, 1024);
            if (unsafeValidation.valid() || unsafeValidation.expandedBytes() <= 0
                    || unsafeValidation.expandedBytes() > 1024
                    || com.lowdragmc.photon.network.FxPackSync.isValidFxPack(unsafeArchive)) {
                throw new IllegalStateException("FX pack validation accepted a traversal entry or lost its bounded work count");
            }
        } finally {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(unsafeArchive);
            Files.deleteIfExists(directory);
        }
    }

    private static void verifySyncPacketTokens() {
        var pack = new com.lowdragmc.photon.network.FxPackSync.PackEntry(
                "test.fxpack", 3, "0".repeat(64));
        var manifest = new com.lowdragmc.photon.network.FxPackSync.ManifestPacket(17, java.util.List.of(pack));
        var manifestBuffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            com.lowdragmc.photon.network.FxPackSync.ManifestPacket.encode(manifest, manifestBuffer);
            var decoded = com.lowdragmc.photon.network.FxPackSync.ManifestPacket.decode(manifestBuffer);
            if (decoded.requestToken() != 17 || !decoded.entries().equals(java.util.List.of(pack))) {
                throw new IllegalStateException("FX pack manifest request token did not survive packet encoding");
            }
        } finally {
            manifestBuffer.release();
        }

        var request = new com.lowdragmc.photon.network.FxPackSync.RequestPacket(17, pack.sha256(), 24);
        var requestBuffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            com.lowdragmc.photon.network.FxPackSync.RequestPacket.encode(request, requestBuffer);
            var decoded = com.lowdragmc.photon.network.FxPackSync.RequestPacket.decode(requestBuffer);
            if (!decoded.equals(request)) {
                throw new IllegalStateException("FX pack request token did not survive packet encoding");
            }
        } finally {
            requestBuffer.release();
        }

        var refresh = com.lowdragmc.photon.network.FxPackSync.refreshRequest();
        var refreshBuffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            com.lowdragmc.photon.network.FxPackSync.RequestPacket.encode(refresh, refreshBuffer);
            var decoded = com.lowdragmc.photon.network.FxPackSync.RequestPacket.decode(refreshBuffer);
            if (!decoded.isRefresh() || !decoded.equals(refresh)) {
                throw new IllegalStateException("FX pack refresh request did not survive packet encoding");
            }
        } finally {
            refreshBuffer.release();
        }

        var chunk = new com.lowdragmc.photon.network.FxPackSync.ChunkPacket(
                17, pack.sha256(), 0, pack.size(), new byte[]{1, 2, 3});
        var chunkBuffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            com.lowdragmc.photon.network.FxPackSync.ChunkPacket.encode(chunk, chunkBuffer);
            var decoded = com.lowdragmc.photon.network.FxPackSync.ChunkPacket.decode(chunkBuffer);
            if (decoded.requestToken() != chunk.requestToken() || !decoded.sha256().equals(chunk.sha256())
                    || decoded.offset() != chunk.offset() || decoded.totalSize() != chunk.totalSize()
                    || !java.util.Arrays.equals(decoded.bytes(), chunk.bytes())) {
                throw new IllegalStateException("FX pack chunk request token did not survive packet encoding");
            }
        } finally {
            chunkBuffer.release();
        }
    }

    /**
     * Entity FX packets must bound their id list against the bytes actually left, <b>including</b> the trailing
     * replay flag — counting it as payload once let a crafted packet pass the check and read past the buffer.
     */
    private static void verifyEffectCommandCodecBounds() {
        var id = new net.minecraft.resources.ResourceLocation("test", "effect");
        var good = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            writeEffectCommandPrefix(good, id);
            good.writeVarInt(0); // no ids
            good.writeBoolean(false); // replay flag
            com.lowdragmc.photon.command.EntityEffectCommand.decodePacket(good); // must not throw
        } finally {
            good.release();
        }
        var bad = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            writeEffectCommandPrefix(bad, id);
            bad.writeVarInt(2); // claims two ids...
            bad.writeVarInt(1); // ...but only one id byte follows
            bad.writeBoolean(false); // the trailing replay flag the bound check must reserve
            try {
                com.lowdragmc.photon.command.EntityEffectCommand.decodePacket(bad);
                throw new IllegalStateException("Entity FX packet accepted an id list larger than the remaining bytes");
            } catch (io.netty.handler.codec.DecoderException expected) {
                // the clean rejection the trailing-byte bound guarantees
            }
        } finally {
            bad.release();
        }
    }

    private static void writeEffectCommandPrefix(net.minecraft.network.FriendlyByteBuf buffer,
                                                 net.minecraft.resources.ResourceLocation id) {
        buffer.writeResourceLocation(id);
        for (int i = 0; i < 9; i++) buffer.writeDouble(0d); // offset, rotation, scale
        buffer.writeVarInt(0); // delay
        buffer.writeBoolean(false); // forcedDeath
        buffer.writeBoolean(false); // allowMulti
        buffer.writeEnum(com.lowdragmc.photon.client.fx.EntityEffectExecutor.AutoRotate.NONE);
    }

    /**
     * The names {@link LocalFxResourceUpload} skips when packing {@code ldlib2/assets} must be exactly the names
     * server-side pack validation rejects as zip entries — otherwise one oddly named file either breaks the whole
     * transfer late or is skipped while the pack would have carried it fine.
     */
    private static void verifyUnsafeEntryNameParity() throws Exception {
        for (var name : java.util.List.of("ns/fx/a.fx", "ns/textures/with space.png",
                "ns/fx/nombre-único.fx", "a.b-c_d/e.fx", "ns/fx/..hidden.fx")) {
            if (!LocalFxResourceUpload.isSafeEntryName(name)) {
                throw new IllegalStateException("LDLib2 export would skip a safe relative path: " + name);
            }
        }
        var unsafe = java.util.List.of("ns\\fx\\a.fx", "ns/fo:o.fx", "..", "a/../b.fx", "a/./b.fx",
                "", " ", "/abs.fx", "a//b.fx", "a/\u0001b.fx");
        for (var name : unsafe) {
            if (LocalFxResourceUpload.isSafeEntryName(name)) {
                throw new IllegalStateException("LDLib2 export would accept an unsafe relative path: " + name);
            }
        }
        var directory = Files.createTempDirectory("photon-entry-parity-");
        try {
            for (var name : unsafe) {
                var archive = directory.resolve("parity.fxpack");
                Files.deleteIfExists(archive);
                try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                    zip.putNextEntry(new ZipEntry("assets/" + name));
                    zip.write(1);
                    zip.closeEntry();
                }
                if (com.lowdragmc.photon.network.FxPackSync.isValidFxPack(archive)) {
                    throw new IllegalStateException("Pack validation accepted the unsafe entry " + name);
                }
            }
            var safe = directory.resolve("safe.fxpack");
            try (var zip = new ZipOutputStream(Files.newOutputStream(safe))) {
                zip.putNextEntry(new ZipEntry("assets/ns/fx/a.fx"));
                zip.write(new byte[]{1, 2, 3});
                zip.closeEntry();
            }
            if (!com.lowdragmc.photon.network.FxPackSync.isValidFxPack(safe)) {
                throw new IllegalStateException("Pack validation rejected a pack of safe entry names");
            }
        } finally {
            try (var files = Files.list(directory)) {
                for (var file : files.toList()) Files.deleteIfExists(file);
            }
            Files.deleteIfExists(directory);
        }
    }

    private static void put(Path path, String value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, value);
    }
}
