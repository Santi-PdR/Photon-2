package com.lowdragmc.photon.client.util;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.fx.LooseTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Textures already loaded by the game — Photon, other mods, mounted FX packs — so the editor can
 * pick one that another effect already uses, without copying it into the workspace folder.
 */
@OnlyIn(Dist.CLIENT)
public final class PhotonTextureCatalogue {
    private static final int MAX_CATALOGUE_ENTRIES = 20_000;
    /** Avoid rescanning large mod/resource-pack trees on each keystroke in the texture search box. */
    private static final long AVAILABLE_CACHE_TTL_NANOS = 30_000_000_000L;
    private record CatalogueSnapshot(List<ResourceLocation> locations, List<String> normalizedNames, boolean truncated) {
        private static final CatalogueSnapshot EMPTY = new CatalogueSnapshot(List.of(), List.of(), false);
    }

    private static volatile CatalogueSnapshot availableCache = CatalogueSnapshot.EMPTY;
    private static volatile boolean loadedListTruncated;
    private static volatile long availableCacheAt;
    private static volatile boolean availableCacheValid;
    private static volatile boolean capWarningLogged;

    private PhotonTextureCatalogue() {
    }

    public static synchronized void invalidate() {
        availableCacheValid = false;
        availableCache = CatalogueSnapshot.EMPTY;
        availableCacheAt = 0;
        loadedListTruncated = false;
        capWarningLogged = false;
    }

    /**
     * Non-vanilla {@code textures/**.png} resources, capped at the first 20,000 ids in stable order.
     * Vanilla is omitted because it is thousands of block/model files.
     */
    public static List<ResourceLocation> listLoaded() {
        var manager = Minecraft.getInstance().getResourceManager();
        if (manager == null) {
            loadedListTruncated = false;
            return List.of();
        }
        var sorted = new java.util.TreeSet<ResourceLocation>(Comparator.comparing(ResourceLocation::toString));
        boolean truncated = false;
        for (var location : manager.listResources("textures", candidate -> candidate.getPath().startsWith("textures/")
                        && candidate.getPath().endsWith(".png")).keySet()) {
            if (location.getNamespace().equals("minecraft")) continue;
            sorted.add(location);
            if (sorted.size() > MAX_CATALOGUE_ENTRIES) {
                sorted.pollLast();
                truncated = true;
            }
        }
        loadedListTruncated = truncated;
        warnIfTruncated(truncated);
        return List.copyOf(sorted);
    }

    /** Mounted pack textures plus author files in Photon/LDLib/resource-pack folders that are not mounted. */
    public static List<ResourceLocation> listAvailable() {
        return snapshot().locations();
    }

    /** Whether a safety limit omitted, or may have omitted, texture choices from the cached catalogue. */
    public static boolean isCatalogueIncomplete() {
        return snapshot().truncated();
    }

    private static CatalogueSnapshot snapshot() {
        long now = System.nanoTime();
        if (availableCacheValid && now - availableCacheAt < AVAILABLE_CACHE_TTL_NANOS) return availableCache;
        synchronized (PhotonTextureCatalogue.class) {
            now = System.nanoTime();
            if (availableCacheValid && now - availableCacheAt < AVAILABLE_CACHE_TTL_NANOS) return availableCache;
            var found = new java.util.TreeSet<ResourceLocation>(Comparator.comparing(ResourceLocation::toString));
            var loaded = listLoaded();
            boolean truncated = loadedListTruncated;
            truncated |= addBounded(found, loaded);
            var loose = LooseTextures.listLooseTextureIds();
            truncated |= LooseTextures.looseTextureCatalogueMayBeIncomplete();
            truncated |= addBounded(found, loose);
            warnIfTruncated(truncated);
            var locations = List.copyOf(found);
            var normalized = locations.stream().map(location -> location.toString().toLowerCase(Locale.ROOT)).toList();
            availableCache = new CatalogueSnapshot(locations, normalized, truncated);
            // Start the TTL after the expensive filesystem/resource-manager scan finishes.
            availableCacheAt = System.nanoTime();
            availableCacheValid = true;
            return availableCache;
        }
    }

    private static boolean addBounded(java.util.TreeSet<ResourceLocation> target, Iterable<ResourceLocation> locations) {
        boolean truncated = false;
        for (var location : locations) {
            target.add(location);
            if (target.size() > MAX_CATALOGUE_ENTRIES) {
                target.pollLast();
                truncated = true;
            }
        }
        return truncated;
    }

    private static void warnIfTruncated(boolean truncated) {
        if (truncated && !capWarningLogged) {
            capWarningLogged = true;
            Photon.LOGGER.warn("Photon's texture picker catalogue hit a safety limit; some texture choices may be omitted");
        }
    }

    public static List<ResourceLocation> matching(String query) {
        return matching(query, Integer.MAX_VALUE);
    }

    /** Return at most {@code limit} sorted results so a picker cannot create thousands of UI elements at once. */
    public static List<ResourceLocation> matching(String query, int limit) {
        return matching(query, limit, location -> true);
    }

    /** Like {@link #matching(String, int)}, skipping ids that the caller already displays elsewhere. */
    public static List<ResourceLocation> matching(String query, int limit, Predicate<ResourceLocation> include) {
        if (limit <= 0) return List.of();
        var snapshot = snapshot();
        var search = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        var matched = new ArrayList<ResourceLocation>(Math.min(snapshot.locations().size(), limit));
        for (int i = 0; i < snapshot.locations().size(); i++) {
            var location = snapshot.locations().get(i);
            if ((search.isEmpty() || snapshot.normalizedNames().get(i).contains(search))
                    && (include == null || include.test(location))) {
                matched.add(location);
                if (matched.size() == limit) break;
            }
        }
        return matched;
    }
}
