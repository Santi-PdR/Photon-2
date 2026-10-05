package com.lowdragmc.photon.client.fx.fxpack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
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

    private static void writeFx(Path path, String assetLocation) throws Exception {
        var fx = new CompoundTag();
        fx.putString("texture", assetLocation);
        var bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(fx, bytes);
        Files.write(path, bytes.toByteArray());
    }
}
