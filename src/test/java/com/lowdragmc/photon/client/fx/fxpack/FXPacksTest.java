package com.lowdragmc.photon.client.fx.fxpack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FXPacksTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesNestedLDLibEditorResourcePackRoot() throws Exception {
        var ldlibAssets = tempDir.resolve("ldlib2/assets");
        var expectedPackRoot = ldlibAssets.resolve("ldlib2");
        var shader = expectedPackRoot.resolve("assets/ldlib2/shaders/core/tornado_body.json");
        Files.createDirectories(shader.getParent());
        Files.writeString(shader, "{}");

        assertEquals(expectedPackRoot, FXPacks.ldlib2EditorPackRoot(ldlibAssets));
    }

    @Test
    void nestedLDLibEditorPackServesShaderByItsResourceLocation() throws Exception {
        var ldlibAssets = tempDir.resolve("ldlib2/assets");
        var expectedPackRoot = ldlibAssets.resolve("ldlib2");
        var shader = expectedPackRoot.resolve("assets/ldlib2/shaders/core/tornado_body.json");
        Files.createDirectories(shader.getParent());
        Files.writeString(shader, "{\"fragment\":\"ldlib2:tornado_body\"}");

        var packRoot = FXPacks.ldlib2EditorPackRoot(ldlibAssets);
        assertNotNull(packRoot);
        try (var pack = new PathPackResources("test/ldlib2-editor", packRoot, false)) {
            var resource = pack.getResource(PackType.CLIENT_RESOURCES,
                    ResourceLocation.fromNamespaceAndPath("ldlib2", "shaders/core/tornado_body.json"));
            assertNotNull(resource);
            try (var input = resource.get()) {
                assertEquals("{\"fragment\":\"ldlib2:tornado_body\"}",
                        new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void doesNotMountLDLibEditorAssetsWithoutAResourceTree() throws Exception {
        var ldlibAssets = tempDir.resolve("ldlib2/assets");
        Files.createDirectories(ldlibAssets.resolve("ldlib2"));

        assertNull(FXPacks.ldlib2EditorPackRoot(ldlibAssets));
    }

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

    @Test
    void corruptShaderMetadataAbortsSweepInsteadOfDeletingUnmarkedFiles() throws Exception {
        var pack = tempDir.resolve("corrupt-shader.fxpack");
        var removedId = ResourceLocation.fromNamespaceAndPath("demo", "remove_me");

        try (var zip = FileSystems.newFileSystem(pack, Map.of("create", "true"))) {
            var fxDir = zip.getPath("assets/demo/fx");
            var shaderDir = zip.getPath("assets/demo/shaders/core");
            var textureDir = zip.getPath("assets/demo/textures");
            Files.createDirectories(fxDir);
            Files.createDirectories(shaderDir);
            Files.createDirectories(textureDir);
            writeFx(fxDir.resolve("remove_me.fx"), "demo:textures/remove.png");
            writeShaderFx(fxDir.resolve("keep_me.fx"), "demo:broken");
            Files.writeString(shaderDir.resolve("broken.json"), "{ malformed json");
            Files.write(textureDir.resolve("unmarked.png"), new byte[]{7});
        }

        FXPacks.removeFx(pack.toFile(), removedId);

        try (var zip = FileSystems.newFileSystem(pack, Map.of())) {
            assertTrue(Files.exists(zip.getPath("assets/demo/fx/keep_me.fx")));
            assertTrue(Files.exists(zip.getPath("assets/demo/shaders/core/broken.json")));
            assertTrue(Files.exists(zip.getPath("assets/demo/textures/unmarked.png")));
        }
    }

    @Test
    void gcKeepsNestedPackedLibraryResourcesAndTheirAssets() throws Exception {
        var pack = tempDir.resolve("nested-library.fxpack");
        var outerPath = "file(assets/photon_fx/render_graph/outer.nbt)";
        var innerPath = "file(assets/photon_fx/fullscreen_graph/inner.nbt)";

        try (var zip = FileSystems.newFileSystem(pack, Map.of("create", "true"))) {
            var fx = new CompoundTag();
            fx.putString("effect", outerPath);
            fx.putString("invalidTraversal", "file(assets/demo/../../photon_fx/orphan/unused.nbt)");
            fx.putString("invalidNamespace", "file(assets/../photon_fx/orphan/unused.nbt)");
            writeCompressedFx(zip.getPath("assets/demo/fx/nested/library.fx"), fx);

            var outer = new CompoundTag();
            outer.putString("nestedEffect", innerPath);
            writeLibraryTag(zip.getPath("assets/photon_fx/render_graph/outer.nbt"), outer);

            var inner = new CompoundTag();
            inner.putString("texture", "demo:textures/fx/reachable.png");
            writeLibraryTag(zip.getPath("assets/photon_fx/fullscreen_graph/inner.nbt"), inner);

            Files.createDirectories(zip.getPath("assets/demo/textures/fx"));
            Files.write(zip.getPath("assets/demo/textures/fx/reachable.png"), new byte[]{1});
            Files.createDirectories(zip.getPath("assets/photon_fx/orphan"));
            Files.write(zip.getPath("assets/photon_fx/orphan/unused.nbt"), new byte[]{2});
            Files.write(zip.getPath("assets/demo/textures/fx/orphan.png"), new byte[]{3});
        }

        try (var zip = FileSystems.newFileSystem(pack, Map.of())) {
            FXPacks.gc(zip);
            assertTrue(Files.exists(zip.getPath("assets/demo/fx/nested/library.fx")));
            assertTrue(Files.exists(zip.getPath("assets/photon_fx/render_graph/outer.nbt")));
            assertTrue(Files.exists(zip.getPath("assets/photon_fx/fullscreen_graph/inner.nbt")));
            assertTrue(Files.exists(zip.getPath("assets/demo/textures/fx/reachable.png")));
            assertFalse(Files.exists(zip.getPath("assets/photon_fx/orphan/unused.nbt")));
            assertFalse(Files.exists(zip.getPath("assets/demo/textures/fx/orphan.png")));
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
        writeCompressedFx(path, fx);
    }

    private static void writeCompressedFx(Path path, CompoundTag fx) throws Exception {
        Files.createDirectories(path.getParent());
        var bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(fx, bytes);
        Files.write(path, bytes.toByteArray());
    }

    private static void writeLibraryTag(Path path, CompoundTag tag) throws Exception {
        Files.createDirectories(path.getParent());
        try (var output = new DataOutputStream(Files.newOutputStream(path))) {
            NbtIo.write(tag, output);
        }
    }
}
