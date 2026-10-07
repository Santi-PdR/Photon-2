package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
