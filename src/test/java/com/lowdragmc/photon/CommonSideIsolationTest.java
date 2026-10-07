package com.lowdragmc.photon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommonSideIsolationTest {
    private static final List<String> COMMON_CLASSFILES = List.of(
            "com/lowdragmc/photon/Photon.class",
            "com/lowdragmc/photon/PhotonCommonProxy.class",
            "com/lowdragmc/photon/PhotonNetworking.class",
            "com/lowdragmc/photon/PhotonRegistries.class",
            "com/lowdragmc/photon/ServerCommands.class",
            "com/lowdragmc/photon/command/EffectCommand.class",
            "com/lowdragmc/photon/command/FxLocationArgument.class",
            "com/lowdragmc/photon/command/BlockEffectCommand.class",
            "com/lowdragmc/photon/command/EntityEffectCommand.class",
            "com/lowdragmc/photon/command/EntityEffectCommand$AutoRotateType.class",
            "com/lowdragmc/photon/command/RemoveBlockEffectCommand.class",
            "com/lowdragmc/photon/command/RemoveEntityEffectCommand.class"
    );

    @Test
    void commonEntrypointAndPacketClassfilesDoNotLinkClientOnlyMinecraftApis() throws IOException {
        var classLoader = getClass().getClassLoader();
        for (var classfile : COMMON_CLASSFILES) {
            byte[] bytes;
            try (var stream = classLoader.getResourceAsStream(classfile)) {
                assertNotNull(stream, "Missing common classfile " + classfile);
                bytes = stream.readAllBytes();
            }
            var constantPool = new String(bytes, StandardCharsets.ISO_8859_1);
            assertFalse(constantPool.contains("net/minecraft/client/"),
                    classfile + " directly references a client-only Minecraft class");
            assertFalse(constantPool.contains("com/mojang/blaze3d/"),
                    classfile + " directly references client-only Blaze3D classes");
        }
    }
}
