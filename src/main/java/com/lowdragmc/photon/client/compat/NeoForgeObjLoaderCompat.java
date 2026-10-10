package com.lowdragmc.photon.client.compat;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Compatibility helper for resource packs authored against NeoForge's built-in OBJ loader ID. */
public final class NeoForgeObjLoaderCompat {
    private static final ResourceLocation NEOFORGE_OBJ = ResourceLocation.fromNamespaceAndPath("neoforge", "obj");

    private NeoForgeObjLoaderCompat() {
    }

    /**
     * Installs the Forge OBJ loader under NeoForge's resource-pack ID, without replacing an existing
     * registration from another compatibility mod.
     *
     * @return true if this call installed the alias
     */
    public static <T> boolean registerAlias(Map<ResourceLocation, T> loaders, T forgeObjLoader) {
        return loaders.putIfAbsent(NEOFORGE_OBJ, forgeObjLoader) == null;
    }
}
