package com.lowdragmc.photon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonSideIsolationTest {
    private static final List<String> COMMON_CLASSFILES = List.of(
            "com/lowdragmc/photon/Photon.class",
            "com/lowdragmc/photon/PhotonConfig.class",
            "com/lowdragmc/photon/PhotonCommonListeners.class",
            "com/lowdragmc/photon/PhotonCommonProxy.class",
            "com/lowdragmc/photon/integration/PhotonLDLibPlugin.class",
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
    void commonEntrypointPacketAndPluginClassfilesDoNotLinkClientOnlyApis() throws IOException {
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
            if (classfile.equals("com/lowdragmc/photon/Photon.class")) {
                assertTrue(constantPool.contains("net/minecraftforge/fml/DistExecutor"),
                        "common entrypoint must use Forge's distribution gate");
                assertTrue(constantPool.contains("safeRunForDist"),
                        "proxy selection must be gated by physical distribution");
                assertTrue(constantPool.contains("safeCallWhenOn"),
                        "client-only compatibility checks must be gated by physical distribution");
            }
            if (classfile.equals("com/lowdragmc/photon/integration/PhotonLDLibPlugin.class")) {
                assertFalse(constantPool.contains("com/lowdragmc/photon/client/"),
                        "common LDLib2 plugin directly references Photon client classes");
                assertTrue(constantPool.contains("net/minecraftforge/fml/DistExecutor"),
                        "common LDLib2 plugin must use Forge's distribution gate");
                assertTrue(constantPool.contains("safeRunWhenOn"),
                        "common LDLib2 plugin must guard client accessor registration");
                assertTrue(constantPool.contains("com/lowdragmc/photon/integration/PhotonLDLibClientPlugin"),
                        "common LDLib2 plugin must retain client accessor registration");
            }
            if (classfile.equals("com/lowdragmc/photon/PhotonRegistries.class")) {
                assertTrue(constantPool.contains("net/minecraftforge/fml/DistExecutor"),
                        "client-only registries must initialize behind Forge's distribution gate");
                assertTrue(constantPool.contains("safeRunWhenOn"),
                        "client-only registry scanning must not run on a dedicated server");
            }
            if (classfile.equals("com/lowdragmc/photon/command/FxLocationArgument.class")) {
                assertTrue(constantPool.contains("net/minecraftforge/fml/DistExecutor"),
                        "client resource suggestions must be side-gated");
                assertTrue(constantPool.contains("unsafeCallWhenOn"),
                        "client FX classes must only be resolved for client-side suggestions");
                assertTrue(constantPool.contains("com/lowdragmc/photon/client/fx/FXHelper"),
                        "the guarded client suggestion path must remain present");
            }
        }
    }

    @Test
    void allCommonProductionClassfilesAvoidDirectClientRenderingLinks() throws Exception {
        var classesRoot = Path.of(Photon.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve("com/lowdragmc/photon");
        assertTrue(Files.isDirectory(classesRoot), "Missing compiled Photon classes at " + classesRoot);

        try (Stream<Path> classfiles = Files.walk(classesRoot)) {
            for (var path : classfiles.filter(p -> p.toString().endsWith(".class")).toList()) {
                var relative = classesRoot.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
                if (isClientOnlyClassfile(relative)) continue;

                var constantPool = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
                assertFalse(constantPool.contains("net/minecraft/client/"),
                        relative + " directly references a client-only Minecraft class");
                assertFalse(constantPool.contains("com/mojang/blaze3d/"),
                        relative + " directly references client-only Blaze3D classes");
            }
        }
    }

    private static boolean isClientOnlyClassfile(String relative) {
        var path = relative.toLowerCase(Locale.ROOT);
        return path.startsWith("client/")
                || path.startsWith("gui/")
                || path.startsWith("uitest/")
                || path.startsWith("core/mixins/")
                || path.endsWith("$client.class")
                || path.equals("integration/photonldlibclientplugin.class");
    }
}
