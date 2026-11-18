package com.lowdragmc.photon.client.fx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/** Regression coverage for loose-asset path safety and bounded texture-catalogue scans. */
public final class LooseTexturePathRegression {
    private LooseTexturePathRegression() {}

    public static void verify() throws Exception {
        var sandbox = Files.createTempDirectory("photon-loose-texture-path-");
        var assets = Files.createDirectory(sandbox.resolve("assets"));
        var outside = Files.createDirectory(sandbox.resolve("outside"));
        try {
            var normal = assets.resolve("example/textures/effect.png");
            if (!LooseTextures.canonicalContainedPath(assets, normal).startsWith(assets.toRealPath())) {
                throw new IllegalStateException("A normal loose texture destination was rejected");
            }
            verifyCatalogueBounds(sandbox);
            verifyPngBounds(sandbox);
            var escapingId = new net.minecraft.resources.ResourceLocation("probe", "../../escape.png");
            if (FXSourceReport.knownFile(sandbox, "photon/workspace", escapingId) != null) {
                throw new IllegalStateException("FX provenance resolved a traversal id outside its owned root");
            }
            var link = assets.resolve("example");
            try {
                Files.createSymbolicLink(link, outside);
            } catch (UnsupportedOperationException | SecurityException | IOException unavailable) {
                // Some Windows configurations disallow creating symbolic links for unprivileged processes.
                return;
            }
            try {
                LooseTextures.canonicalContainedPath(assets, link.resolve("textures/effect.png"));
                throw new IllegalStateException("A symbolic link escaped the Photon assets root");
            } catch (IOException expected) {
                // Canonical containment detects an existing symlink before the destination is written.
            }
        } finally {
            Files.walkFileTree(sandbox, new SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                    if (failure != null) throw failure;
                    Files.deleteIfExists(dir);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static void verifyPngBounds(Path sandbox) throws IOException {
        var oversized = sandbox.resolve("oversized.png");
        var header = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10,
                0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 0, 0, 0, 0, 0};
        writePngInt(header, 16, 4_097);
        writePngInt(header, 20, 4_097);
        Files.write(oversized, header);
        try {
            LooseTextures.validateLoosePng(oversized);
            throw new IllegalStateException("An oversized loose PNG was allowed to reach NativeImage decoding");
        } catch (IOException expected) {
            // The pixel cap is checked from IHDR before allocating the decoded image.
        }
    }

    private static void writePngInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private static void verifyCatalogueBounds(Path sandbox) throws IOException {
        var firstNamespace = Files.createDirectories(sandbox.resolve("pack-a/assets/probe_a"));
        var firstTextures = Files.createDirectories(firstNamespace.resolve("textures"));
        for (int i = 0; i < 3; i++) Files.writeString(firstTextures.resolve("a" + i + ".png"), "texture");
        Files.writeString(firstTextures.resolve("ignore.txt"), "not a texture");
        var vanillaNamespace = Files.createDirectories(sandbox.resolve("pack-vanilla/assets/minecraft"));
        var vanillaTextures = Files.createDirectories(vanillaNamespace.resolve("textures"));
        Files.writeString(vanillaTextures.resolve("vanilla.png"), "texture");
        var secondNamespace = Files.createDirectories(sandbox.resolve("pack-b/assets/probe_b"));
        var secondTextures = Files.createDirectories(secondNamespace.resolve("textures"));
        Files.writeString(secondTextures.resolve("unscanned.png"), "texture");

        var namespaces = new java.util.LinkedHashMap<String, java.util.List<Path>>();
        namespaces.put("probe_a", java.util.List.of(firstNamespace));
        namespaces.put("probe_b", java.util.List.of(secondNamespace));
        namespaces.put("minecraft", java.util.List.of(vanillaNamespace));
        var complete = LooseTextures.scanTextureFolders(namespaces, 100, 100);
        if (complete.truncated() || complete.unreadable() || complete.scannedCandidates() != 7 || complete.locations().size() != 4
                || complete.locations().stream().anyMatch(id -> id.getNamespace().equals("minecraft")
                || !id.getPath().endsWith(".png"))
                || !complete.locations().equals(complete.locations().stream()
                .sorted(java.util.Comparator.comparing(net.minecraft.resources.ResourceLocation::toString)).toList())) {
            throw new IllegalStateException("Unbounded loose texture scan was marked incomplete or returned unstable results: " + complete);
        }
        var globalBound = LooseTextures.scanTextureFolders(namespaces, 100, 5);
        if (!globalBound.truncated() || globalBound.unreadable() || globalBound.scannedCandidates() != 5
                || globalBound.locations().size() != 3
                || globalBound.locations().stream().anyMatch(id -> !id.getNamespace().equals("probe_a"))) {
            throw new IllegalStateException("Loose texture scanning did not enforce its global candidate bound: " + globalBound);
        }
        var resultBound = LooseTextures.scanTextureFolders(namespaces, 1, 100);
        if (!resultBound.truncated() || resultBound.locations().size() != 1) {
            throw new IllegalStateException("Loose texture scanning did not report a reached result cap: " + resultBound);
        }
    }
}
