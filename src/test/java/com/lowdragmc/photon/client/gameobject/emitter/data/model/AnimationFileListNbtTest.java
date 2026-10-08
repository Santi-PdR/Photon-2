package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AnimationFileListNbtTest {
    @Test
    void writesReferenceCompatibleResourceLocationList() {
        var locations = List.of(ResourceLocation.fromNamespaceAndPath("example", "animations/walk.gltf"),
                ResourceLocation.fromNamespaceAndPath("other", "run.glb"));

        CompoundTag serialized = AnimationFileListNbt.write(locations);

        var files = serialized.getList("animationFiles", net.minecraft.nbt.Tag.TAG_STRING);
        assertEquals(List.of("example:animations/walk.gltf", "other:run.glb"),
                List.of(files.getString(0), files.getString(1)));
        assertEquals(locations, AnimationFileListNbt.read(serialized));
    }

    @Test
    void skipsInvalidResourceLocationsAndTreatsMissingFieldAsEmpty() {
        var serialized = new CompoundTag();
        var files = new ListTag();
        files.add(StringTag.valueOf("example:animations/idle.gltf"));
        files.add(StringTag.valueOf("not a resource location"));
        serialized.put("animationFiles", files);

        assertEquals(List.of(ResourceLocation.fromNamespaceAndPath("example", "animations/idle.gltf")),
                AnimationFileListNbt.read(serialized));
        assertEquals(List.of(), AnimationFileListNbt.read(new CompoundTag()));
        assertEquals(List.of(), AnimationFileListNbt.read(StringTag.valueOf("not a compound")));
    }

    @Test
    void movesLegacyStringFieldRecursivelyIntoAdditionalNbt() {
        var root = new CompoundTag();
        var sourceData = new CompoundTag();
        sourceData.putString("animationFiles", "example:walk.glb; other:run.gltf Upper:bad");
        root.put("data", sourceData);

        AnimationFileListNbt.migrateLegacyConfig(root);

        assertEquals(List.of(ResourceLocation.fromNamespaceAndPath("example", "walk.glb"),
                        ResourceLocation.fromNamespaceAndPath("other", "run.gltf")),
                AnimationFileListNbt.read(sourceData.get("_additional")));
        assertFalse(sourceData.contains("animationFiles"));
    }

    @Test
    void readsLegacyStringAdditionalNbt() {
        var serialized = new CompoundTag();
        serialized.putString("animationFiles", "example:walk.glb other:run.gltf");

        assertEquals(List.of(ResourceLocation.fromNamespaceAndPath("example", "walk.glb"),
                        ResourceLocation.fromNamespaceAndPath("other", "run.gltf")),
                AnimationFileListNbt.read(serialized));
    }
}
