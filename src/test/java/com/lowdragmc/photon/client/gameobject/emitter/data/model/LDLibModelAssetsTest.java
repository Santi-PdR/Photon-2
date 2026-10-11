package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LDLibModelAssetsTest {
    @TempDir
    Path assetsDir;

    @Test
    void resolvesEditableJsonModelsFromTheLdLibNamespaceTree() throws Exception {
        var model = assetsDir.resolve("ldlib2/assets/ldlib2/models/block/custom.json");
        Files.createDirectories(model.getParent());
        Files.writeString(model, "{}");

        assertEquals(model, LDLibModelAssets.findNested(assetsDir,
                ResourceLocation.fromNamespaceAndPath("ldlib2", "block/custom"), "models/block/custom.json"));
    }

    @Test
    void doesNotResolvePathsOutsideTheAssetsRoot() {
        assertNull(LDLibModelAssets.findNested(assetsDir,
                ResourceLocation.fromNamespaceAndPath("ldlib2", "block/custom"), "../../outside.json"));
    }
}
