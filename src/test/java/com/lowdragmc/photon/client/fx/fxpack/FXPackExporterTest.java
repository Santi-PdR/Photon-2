package com.lowdragmc.photon.client.fx.fxpack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FXPackExporterTest {
    @Test
    void namespaceReplacesCharactersThatMinecraftDoesNotAllow() {
        assertEquals("team_effects", FXPackExporter.sanitizeNamespace("Team/Effects"));
        assertEquals("___", FXPackExporter.sanitizeNamespace("///"));
    }

    @Test
    void pathPreservesDirectoriesButSanitizesEachSegment() {
        assertEquals("effects/boss_attack", FXPackExporter.sanitizePath("Effects/Boss Attack"));
        assertEquals("///", FXPackExporter.sanitizePath("///"));
    }
}
