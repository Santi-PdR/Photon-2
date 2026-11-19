package com.lowdragmc.photon.client.fx.fxpack;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.network.FxPackSync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipFile;

/**
 * The local LDLib2 asset upload flow ({@code /photon fx send}, {@code export_ldlib2_assets}) builds
 * one archive from {@code <gameDir>/ldlib2/assets} after a chat confirmation. The network half of
 * the flow is manual; this covers the archive half end to end: entries land under
 * {@code assets/<relative>}, a file whose name cannot travel in a zip is skipped with a warning
 * instead of failing the whole transfer, and the result passes the same
 * {@link FxPackSync#isValidFxPack} validation the server applies.
 */
public final class LocalFxResourceUploadRegression {
    private LocalFxResourceUploadRegression() {
    }

    public static void verify() throws Exception {
        var assets = LDLib2.getAssetsDir().toPath().normalize();
        if (!Files.isDirectory(assets, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("<gameDir>/ldlib2/assets was not found");
        }
        var fixture = assets.resolve("upload_smoke");
        Path archive = null;
        boolean unsafeCreated = false;
        try {
            Files.createDirectories(fixture.resolve("fx"));
            Files.createDirectories(fixture.resolve("textures"));
            Files.write(fixture.resolve("fx").resolve("ci_upload.fx"), new byte[]{1, 2, 3});
            Files.write(fixture.resolve("textures").resolve("t.png"), new byte[]{9});
            Path unsafe = fixture.resolve("bad:name.png");
            try {
                Files.write(unsafe, new byte[]{9});
                unsafeCreated = true;
            } catch (IOException | RuntimeException unsupportedOnThisFs) {
                // Windows rejects ':' in file names outright; the skip path still runs on Linux CI
                Photon.LOGGER.info("this filesystem forbids ':' in names; skipping the unsafe-name part");
            }
            archive = LocalFxResourceUpload.createArchive();
            if (!FxPackSync.isValidFxPack(archive)) {
                throw new IllegalStateException("the upload archive failed pack validation");
            }
            try (var zip = new ZipFile(archive.toFile())) {
                if (zip.getEntry("pack.mcmeta") == null) {
                    throw new IllegalStateException("the upload archive has no pack.mcmeta");
                }
                if (zip.getEntry("assets/upload_smoke/fx/ci_upload.fx") == null
                        || zip.getEntry("assets/upload_smoke/textures/t.png") == null) {
                    throw new IllegalStateException("the upload archive did not carry the asset tree under assets/");
                }
                if (unsafeCreated && zip.getEntry("assets/upload_smoke/bad:name.png") != null) {
                    throw new IllegalStateException("an unsafe file name travelled into the upload archive");
                }
            }
        } finally {
            if (archive != null) Files.deleteIfExists(archive);
            deleteRecursively(fixture);
        }
        Photon.LOGGER.info("[upload-archive-smoke] the local LDLib2 asset archive skips unsafe names and passes pack validation");
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
