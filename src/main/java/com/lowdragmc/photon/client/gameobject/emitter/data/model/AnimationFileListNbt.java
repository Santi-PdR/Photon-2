package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.lowdragmc.lowdraglib2.LDLib2;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Reads the current list format and migrates older text-encoded animation file lists. */
final class AnimationFileListNbt {
    private static final String KEY = "animationFiles";
    private static final String ADDITIONAL_KEY = "_additional";

    private AnimationFileListNbt() {
    }

    static CompoundTag write(List<ResourceLocation> locations) {
        CompoundTag result = new CompoundTag();
        ListTag serialized = new ListTag();
        for (ResourceLocation location : locations) serialized.add(StringTag.valueOf(location.toString()));
        result.put(KEY, serialized);
        return result;
    }

    static List<ResourceLocation> read(Tag tag) {
        if (!(tag instanceof CompoundTag compound)) return List.of();
        Tag value = compound.get(KEY);
        if (value instanceof StringTag legacy) return parseLegacyLocations(legacy.getAsString());
        if (!(value instanceof ListTag serialized) || serialized.getElementType() != Tag.TAG_STRING) return List.of();

        List<ResourceLocation> locations = new ArrayList<>(serialized.size());
        for (int i = 0; i < serialized.size(); i++) {
            ResourceLocation location = ResourceLocation.tryParse(serialized.getString(i));
            if (location != null) locations.add(location);
        }
        return List.copyOf(locations);
    }

    static void migrateLegacyConfig(CompoundTag serialized) {
        for (String key : List.copyOf(serialized.getAllKeys())) {
            Tag value = serialized.get(key);
            if (KEY.equals(key)) {
                CompoundTag additional = serialized.getCompound(ADDITIONAL_KEY);
                if (!additional.contains(KEY)) {
                    List<ResourceLocation> locations = value instanceof StringTag legacy
                            ? parseLegacyLocations(legacy.getAsString())
                            : read(write(value instanceof ListTag list ? readList(list) : List.of()));
                    if (!locations.isEmpty()) {
                        additional.put(KEY, writeList(locations));
                        serialized.put(ADDITIONAL_KEY, additional);
                    }
                }
                serialized.remove(KEY);
            } else if (!ADDITIONAL_KEY.equals(key) && value instanceof CompoundTag nested) {
                migrateLegacyConfig(nested);
            }
        }
    }

    private static List<ResourceLocation> readList(ListTag list) {
        if (list.getElementType() != Tag.TAG_STRING) return List.of();
        List<ResourceLocation> locations = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation location = ResourceLocation.tryParse(list.getString(i));
            if (location != null) locations.add(location);
        }
        return locations;
    }

    private static ListTag writeList(List<ResourceLocation> locations) {
        ListTag serialized = new ListTag();
        for (ResourceLocation location : locations) serialized.add(StringTag.valueOf(location.toString()));
        return serialized;
    }

    private static List<ResourceLocation> parseLegacyLocations(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        List<ResourceLocation> locations = new ArrayList<>();
        for (String entry : encoded.split("[,;\\s]+")) {
            if (!entry.isBlank() && LDLib2.isValidResourceLocation(entry.trim())) {
                locations.add(new ResourceLocation(entry.trim()));
            }
        }
        return List.copyOf(locations);
    }
}

