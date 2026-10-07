package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** NBT codec for the external animation resource list persisted by animated glTF sources. */
final class AnimationFileListNbt {
    private static final String KEY = "animationFiles";

    private AnimationFileListNbt() {}

    static CompoundTag write(List<ResourceLocation> locations) {
        var result = new CompoundTag();
        var serialized = new ListTag();
        for (var location : locations) serialized.add(StringTag.valueOf(location.toString()));
        result.put(KEY, serialized);
        return result;
    }

    /** A missing or malformed list is empty, matching the source's clear-before-load behavior. */
    static List<ResourceLocation> read(Tag tag) {
        if (!(tag instanceof CompoundTag compound) || !compound.contains(KEY, Tag.TAG_LIST)) return List.of();
        var serialized = compound.getList(KEY, Tag.TAG_STRING);
        var locations = new ArrayList<ResourceLocation>(serialized.size());
        for (int i = 0; i < serialized.size(); i++) {
            var location = ResourceLocation.tryParse(serialized.getString(i));
            if (location != null) locations.add(location);
        }
        return List.copyOf(locations);
    }
}
