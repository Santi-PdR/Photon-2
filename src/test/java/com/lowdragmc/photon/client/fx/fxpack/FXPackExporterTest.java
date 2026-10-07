package com.lowdragmc.photon.client.fx.fxpack;

import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FXPackExporterTest {
    @Test
    void namespaceReplacesCharactersThatMinecraftDoesNotAllow() {
        assertEquals("team_effects", FXPackExporter.sanitizeNamespace("Team/Effects"));
        assertEquals("___", FXPackExporter.sanitizeNamespace("///"));
        assertEquals("fx", FXPackExporter.sanitizeNamespace(".."));
    }

    @Test
    void pathPreservesDirectoriesButSanitizesEachSegment() {
        assertEquals("effects/boss_attack", FXPackExporter.sanitizePath("Effects/Boss Attack"));
        assertEquals("fx", FXPackExporter.sanitizePath("///"));
        assertEquals("__/__/outside/__", FXPackExporter.sanitizePath("/../../outside/.."));
    }

    @Test
    void resolvesAngleImportsUnderTheNamespacedShaderIncludeDirectory() {
        assertEquals(List.of("minecraft:shaders/include/fog.glsl", "photon:shaders/include/particle.glsl"),
                importStrings("""
                        #moj_import <fog.glsl>
                        #moj_import <photon:particle.glsl>
                        """));
    }

    @Test
    void resolvesQuotedImportsFromTheNamespaceAssetRoot() {
        assertEquals(List.of("minecraft:shaders/custom.glsl", "other:shared/math.glsl"),
                importStrings("""
                        #moj_import "shaders/custom.glsl"
                        #moj_import "other:shared/math.glsl"
                        """));
    }

    @Test
    void deduplicatesImportsAndIgnoresCommentsAndUnsafePaths() {
        assertEquals(List.of("minecraft:shaders/include/common.glsl"),
                importStrings("""
                        // #moj_import <ignored.glsl>
                        #moj_import <common.glsl>
                        #moj_import <common.glsl>
                        #moj_import <../outside.glsl>
                        #moj_import "../../outside.glsl"
                        #moj_import <>
                        """));
    }

    private static List<String> importStrings(String source) {
        return ShaderIncludeResolver.imports(source).stream().map(ResourceLocation::toString).toList();
    }
}
