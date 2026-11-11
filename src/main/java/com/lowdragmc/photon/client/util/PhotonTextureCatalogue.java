package com.lowdragmc.photon.client.util;

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
    private PhotonTextureCatalogue() {
    }

    /**
     * Every non-vanilla {@code textures/**.png} the resource manager can see. Vanilla is omitted
     * because it is thousands of block/model files.
     */
    public static List<ResourceLocation> listLoaded() {
        var manager = Minecraft.getInstance().getResourceManager();
        if (manager == null) return List.of();
        var found = new ArrayList<ResourceLocation>();
        manager.listResources("textures", location -> location.getPath().startsWith("textures/")
                        && location.getPath().endsWith(".png"))
                .keySet().stream()
                .filter(location -> !location.getNamespace().equals("minecraft"))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(found::add);
        return found;
    }

    public static List<ResourceLocation> matching(String query) {
        var all = listLoaded();
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
