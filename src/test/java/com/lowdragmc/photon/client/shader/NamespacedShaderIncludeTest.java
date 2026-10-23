package com.lowdragmc.photon.client.shader;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.FileUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NamespacedShaderIncludeTest {
    @Test
    void resolvesNamespacedAngleImportIntoThatNamespacesIncludeDirectory() {
        assertEquals(new ResourceLocation("photon", "shaders/include/particle.glsl"),
                ResourceLocation.parse(NamespacedShaderInclude.normalize(
                        "shaders/include/photon:particle.glsl", FileUtil::normalizeResourcePath)));
    }

    @Test
    void keepsVanillaUnnamespacedAngleImportBehavior() {
        assertEquals(new ResourceLocation("minecraft", "shaders/include/fog.glsl"),
                ResourceLocation.parse(NamespacedShaderInclude.normalize(
                        "shaders/include/fog.glsl", FileUtil::normalizeResourcePath)));
    }

    @Test
    void supportsNestedNamespacedIncludePaths() {
        assertEquals(new ResourceLocation("photon", "shaders/include/common/particle.glsl"),
                ResourceLocation.parse(NamespacedShaderInclude.normalize(
                        "shaders/include/photon:common/particle.glsl", FileUtil::normalizeResourcePath)));
    }
}
