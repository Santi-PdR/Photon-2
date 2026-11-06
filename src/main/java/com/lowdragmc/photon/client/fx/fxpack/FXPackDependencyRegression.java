package com.lowdragmc.photon.client.fx.fxpack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Exercises the same dependency traversal that runs after each fxpack rewrite. */
public final class FXPackDependencyRegression {
    private FXPackDependencyRegression() {}

    public static void verify() throws Exception {
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
        try {
            try (var zip = FileSystems.newFileSystem(java.net.URI.create("jar:" + archive.toUri()), Map.of("create", "true"))) {
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
            }
        } finally {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(directory);
        }
    }

    private static void put(Path path, String value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, value);
    }
}
