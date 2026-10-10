package com.lowdragmc.photon.client.compat;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForgeObjLoaderCompatTest {
    private static final ResourceLocation NEOFORGE_OBJ =
            ResourceLocation.fromNamespaceAndPath("neoforge", "obj");

    @Test
    void registersForgeObjLoaderUnderNeoForgeId() {
        var loaders = new HashMap<ResourceLocation, String>();

        assertTrue(NeoForgeObjLoaderCompat.registerAlias(loaders, "forge-loader"));
        assertEquals("forge-loader", loaders.get(NEOFORGE_OBJ));
    }

    @Test
    void preservesAnExistingNeoForgeLoaderRegistration() {
        var loaders = new HashMap<ResourceLocation, String>();
        loaders.put(NEOFORGE_OBJ, "existing-loader");

        assertFalse(NeoForgeObjLoaderCompat.registerAlias(loaders, "forge-loader"));
        assertEquals("existing-loader", loaders.get(NEOFORGE_OBJ));
    }
}
