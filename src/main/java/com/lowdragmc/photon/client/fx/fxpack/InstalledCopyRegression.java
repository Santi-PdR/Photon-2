package com.lowdragmc.photon.client.fx.fxpack;

import com.lowdragmc.photon.network.FxPackSync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Smoke regression for {@link ClientFxPackSync#installedCopy}: the server's offer of a pack the player already has
 * installed — same bytes under their own file name in {@code photon/fxpacks} — is met by that file. A copy under our
 * own cache naming ({@code server-<hash>.fxpack}) is not an installed pack (that path keeps its hash check), a pack
 * of another content is not a match however long it is, and neither is a non-pack file with the same bytes. The
 * live half of this check runs at the end of the smoke, against the integrated server's re-offer.
 */
public final class InstalledCopyRegression {
    private InstalledCopyRegression() {
    }

    public static void verify() throws IOException {
        var directory = Files.createTempDirectory("photon-installed-copy-");
        try {
            var content = "installed twin of an offered pack".getBytes(StandardCharsets.UTF_8);
            var other = "other content, exactly as long!!!".getBytes(StandardCharsets.UTF_8);
            check(other.length == content.length, "the probe contents must be the same length");
            var twin = Files.write(directory.resolve("twin.fxpack"), content);
            Files.write(directory.resolve("server-" + "0".repeat(64) + FXPacks.SUFFIX), content);
            Files.write(directory.resolve("other.fxpack"), other);
            Files.write(directory.resolve("notes.txt"), content);
            var entry = new FxPackSync.PackEntry("remote.fxpack", content.length, sha256(content));
            check(twin.equals(ClientFxPackSync.installedCopy(directory, entry)), "the installed twin was not found");
            check(ClientFxPackSync.installedCopy(directory, new FxPackSync.PackEntry("remote.fxpack", content.length,
                    sha256("nothing the folder holds".getBytes(StandardCharsets.UTF_8)))) == null,
                    "a pack of the same length but other content counted as installed");
            check(ClientFxPackSync.installedCopy(directory, new FxPackSync.PackEntry("remote.fxpack", content.length + 1,
                    entry.sha256())) == null, "a pack of another size counted as installed");
            check(ClientFxPackSync.installedCopy(directory.resolve("missing"), entry) == null,
                    "a missing folder reported an installed copy");
            Files.delete(twin);
            check(ClientFxPackSync.installedCopy(directory, entry) == null,
                    "a server-<hash> cache copy or a non-pack file counted as an installed pack");
        } finally {
            try (var files = Files.list(directory)) {
                for (var path : files.toList()) Files.deleteIfExists(path);
            }
            Files.deleteIfExists(directory);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by Java", e);
        }
    }

    private static void check(boolean condition, String problem) {
        if (!condition) throw new IllegalStateException("[installed-copy] " + problem);
    }
}
