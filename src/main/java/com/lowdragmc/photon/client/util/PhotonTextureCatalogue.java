package com.lowdragmc.photon.client.util;

import com.lowdragmc.photon.client.fx.LooseTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Textures already loaded by the game — Photon, other mods, mounted FX packs — so the editor can
 * pick one that another effect already uses, without copying it into the workspace folder.
 */
@OnlyIn(Dist.CLIENT)
public final class PhotonTextureCatalogue {
    private static volatile List<ResourceLocation> availableCache = List.of();
    private static volatile long availableCacheAt;

    private PhotonTextureCatalogue() {
    }

    public static void invalidate() {
        availableCache = List.of();
        availableCacheAt = 0;
    }

    /**
     * Every non-vanilla {@code textures/**.png} the resource manager can see. Vanilla is omitted
     * because it is thousands of block/model files.
     */
    public static List<ResourceLocation> listLoaded() {
        var manager = Minecraft.getInstance().getResourceManager();
        if (manager == null) return List.of();
        return manager.listResources("textures", location -> location.getPath().startsWith("textures/")
                        && location.getPath().endsWith(".png"))
                .keySet().stream()
                .filter(location -> !location.getNamespace().equals("minecraft"))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    /** Mounted pack textures plus author files in Photon/LDLib/resource-pack folders that are not mounted. */
    public static List<ResourceLocation> listAvailable() {
        long now = System.nanoTime();
        var cached = availableCache;
        if (!cached.isEmpty() && now - availableCacheAt < 5_000_000_000L) return cached;
        synchronized (PhotonTextureCatalogue.class) {
            if (!availableCache.isEmpty() && now - availableCacheAt < 5_000_000_000L) return availableCache;
            var found = new java.util.LinkedHashSet<>(listLoaded());
            found.addAll(LooseTextures.listLooseTextureIds());
            availableCache = found.stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
            availableCacheAt = now;
            return availableCache;
        }
    }

    public static List<ResourceLocation> matching(String query) {
        var all = listAvailable();
        if (query == null || query.isBlank()) return all;
        var search = query.toLowerCase(Locale.ROOT);
        var matched = new ArrayList<ResourceLocation>();
        for (var location : all) {
            if (location.toString().toLowerCase(Locale.ROOT).contains(search)) {
                matched.add(location);
            }
        }
        return matched;
    }
}
