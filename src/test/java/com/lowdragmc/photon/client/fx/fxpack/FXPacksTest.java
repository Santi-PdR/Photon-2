package com.lowdragmc.photon.client.fx.fxpack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FXPacksTest {
    @TempDir
    Path tempDir;

    @Test
    void removingEffectKeepsResourcesUsedByOtherEffectsAndCollectsOrphans() throws Exception {
        var pack = tempDir.resolve("effects.fxpack");
        var removedId = ResourceLocation.fromNamespaceAndPath("demo", "nested/remove_me");
        var keptId = ResourceLocation.fromNamespaceAndPath("demo", "nested/keep_me");
        var removedAsset = "demo:textures/fx/removed.png";
        var keptAsset = "demo:textures/fx/kept.png";

        try (var zip = FileSystems.newFileSystem(pack, Map.of("create", "true"))) {
            Files.createDirectories(zip.getPath("assets/demo/fx/nested"));
            Files.createDirectories(zip.getPath("assets/demo/textures/fx"));
            writeFx(zip.getPath("assets/demo/fx/nested/remove_me.fx"), removedAsset);
            writeFx(zip.getPath("assets/demo/fx/nested/keep_me.fx"), keptAsset);
            Files.write(zip.getPath("assets/demo/textures/fx/removed.png"), new byte[]{1});
            Files.write(zip.getPath("assets/demo/textures/fx/kept.png"), new byte[]{2});
            Files.write(zip.getPath("assets/demo/textures/fx/orphan.png"), new byte[]{3});
            Files.writeString(zip.getPath("pack.mcmeta"), "{}");
        }

        assertEquals(Set.of(keptId, removedId), Set.copyOf(FXPacks.listFx(pack.toFile())));
        FXPacks.removeFx(pack.toFile(), removedId);

        try (var zip = FileSystems.newFileSystem(pack, Map.of())) {
            assertFalse(Files.exists(zip.getPath("assets/demo/fx/nested/remove_me.fx")));
            assertTrue(Files.exists(zip.getPath("assets/demo/fx/nested/keep_me.fx")));
            assertFalse(Files.exists(zip.getPath("assets/demo/textures/fx/removed.png")));
            assertTrue(Files.exists(zip.getPath("assets/demo/textures/fx/kept.png")));
            assertFalse(Files.exists(zip.getPath("assets/demo/textures/fx/orphan.png")));
            assertTrue(Files.exists(zip.getPath("pack.mcmeta")));
        }
    }

    @Test
    void removingEffectKeepsShaderIncludesStillUsedByAnotherEffect() throws Exception {
        var pack = tempDir.resolve("shader-effects.fxpack");
        var removedId = ResourceLocation.fromNamespaceAndPath("demo", "remove_shader");
        var keptId = ResourceLocation.fromNamespaceAndPath("demo", "keep_shader");

        try (var zip = FileSystems.newFileSystem(pack, Map.of("create", "true"))) {
            var fxDir = zip.getPath("assets/demo/fx");
            Files.createDirectories(fxDir);
            writeShaderFx(fxDir.resolve("remove_shader.fx"), "demo:removed");
            writeShaderFx(fxDir.resolve("keep_shader.fx"), "demo:kept");

            writeShader(zip, "demo:removed", "removed", "#moj_import <photon:shared.glsl>\n#moj_import <photon:removed.glsl>\n");
            writeShader(zip, "demo:kept", "kept", "#moj_import <photon:shared.glsl>\n");
            Files.createDirectories(zip.getPath("assets/photon/shaders/include"));
            Files.writeString(zip.getPath("assets/photon/shaders/include/shared.glsl"),
                    "#moj_import \"photon:shaders/include/nested.glsl\"\n", StandardCharsets.UTF_8);
            Files.writeString(zip.getPath("assets/photon/shaders/include/nested.glsl"),
                    "#moj_import <photon:shared.glsl>\n// still in use\n", StandardCharsets.UTF_8);
            Files.writeString(zip.getPath("assets/photon/shaders/include/removed.glsl"), "// only removed shader\n");
        }

        FXPacks.removeFx(pack.toFile(), removedId);

        try (var zip = FileSystems.newFileSystem(pack, Map.of())) {
            assertFalse(Files.exists(zip.getPath("assets/demo/shaders/core/removed.json")));
            assertTrue(Files.exists(zip.getPath("assets/demo/shaders/core/kept.json")));
            assertTrue(Files.exists(zip.getPath("assets/demo/shaders/core/kept.vsh")));
            assertTrue(Files.exists(zip.getPath("assets/photon/shaders/include/shared.glsl")));
            assertTrue(Files.exists(zip.getPath("assets/photon/shaders/include/nested.glsl")));
            assertFalse(Files.exists(zip.getPath("assets/photon/shaders/include/removed.glsl")));
        }
    }

    private static void writeShader(FileSystem zip, String shaderId, String programId, String vertexSource) throws Exception {
        var shader = ResourceLocation.tryParse(shaderId);
        var shaderPath = zip.getPath("assets", shader.getNamespace(), "shaders/core", shader.getPath() + ".json");
        Files.createDirectories(shaderPath.getParent());
        Files.writeString(shaderPath, "{\"vertex\":\"demo:" + programId + "\",\"fragment\":\"demo:" + programId + "\"}");
        var vertexPath = zip.getPath("assets/demo/shaders/core", programId + ".vsh");
        var fragmentPath = zip.getPath("assets/demo/shaders/core", programId + ".fsh");
        Files.createDirectories(vertexPath.getParent());
        Files.writeString(vertexPath, vertexSource);
        Files.writeString(fragmentPath, "// fragment shader\n");
    }

    private static void writeShaderFx(Path path, String shaderId) throws Exception {
        var data = new CompoundTag();
        data.putString("shaderLocation", shaderId);
        var fx = new CompoundTag();
        fx.putString("type", "custom_shader");
        fx.put("data", data);
        var bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(fx, bytes);
        Files.write(path, bytes.toByteArray());
    }

    private static void writeFx(Path path, String assetLocation) throws Exception {
        var fx = new CompoundTag();
        fx.putString("texture", assetLocation);
        var bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(fx, bytes);
        Files.write(path, bytes.toByteArray());
    }
}
