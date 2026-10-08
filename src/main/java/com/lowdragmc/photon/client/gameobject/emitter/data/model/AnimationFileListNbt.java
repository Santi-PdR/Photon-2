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
        if (!(tag instanceof CompoundTag compound)) return List.of();
        Tag value = compound.get(KEY);
        if (value instanceof StringTag legacy) return parseLegacyLocations(legacy.getAsString());
        if (!(value instanceof ListTag serialized) || serialized.getElementType() != Tag.TAG_STRING) return List.of();
        var locations = new ArrayList<ResourceLocation>(serialized.size());
        for (int i = 0; i < serialized.size(); i++) {
            var location = ResourceLocation.tryParse(serialized.getString(i));
            if (location != null) locations.add(location);
        }
        return List.copyOf(locations);
    }

    /** Upgrade the old Forge string-backed configurable field before PersistedParser reads a list. */
    static void migrateLegacyConfig(CompoundTag serialized) {
        for (String key : List.copyOf(serialized.getAllKeys())) {
            Tag value = serialized.get(key);
            if (KEY.equals(key) && value instanceof StringTag legacy) {
                serialized.put(KEY, parseLegacy(legacy.getAsString()));
            } else if (value instanceof CompoundTag nested) {
                migrateLegacyConfig(nested);
            }
        }
    }

    private static ListTag parseLegacy(String encoded) {
        var locations = new ListTag();
        for (var location : parseLegacyLocations(encoded)) locations.add(StringTag.valueOf(location.toString()));
        return locations;
    }

    private static List<ResourceLocation> parseLegacyLocations(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        var locations = new ArrayList<ResourceLocation>();
        for (String entry : encoded.split("[,;\\s]+")) {
            var location = ResourceLocation.tryParse(entry.trim());
            if (location != null) locations.add(location);
        }
        return List.copyOf(locations);
    }
}
