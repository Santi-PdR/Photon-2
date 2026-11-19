package com.lowdragmc.photon.client.fx.fxpack;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonNetworking;
import com.lowdragmc.photon.network.FxPackSync;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Packages and uploads or locally exports a player's complete {@code ldlib2/assets} resource tree. */
@OnlyIn(Dist.CLIENT)
public final class LocalFxResourceUpload {
    private static final AtomicBoolean UPLOADING = new AtomicBoolean();
    private static final AtomicBoolean EXPORTING = new AtomicBoolean();
    private static volatile long activeToken = -1;
    private static final int MAX_ENTRIES = 20_000;
    private static final long MAX_ARCHIVE_BYTES = FxPackSync.MAX_PACK_BYTES;
    private static final long MAX_EXPANDED_BYTES = FxPackSync.MAX_EXPANDED_PACK_BYTES;

    private LocalFxResourceUpload() {
    }

    /** Upload the local assets to the server for delivery to the selected player. */
    public static void begin(FxPackSync.UploadPromptPacket prompt) {
        if (!UPLOADING.compareAndSet(false, true)) {
            feedback("Photon: another ldlib2/assets transfer is already running.");
            return;
        }
        activeToken = prompt.token();
        feedback("Photon: packaging ldlib2/assets for " + prompt.recipientName() + "…");
        CompletableFuture.runAsync(() -> upload(prompt));
    }

    /** Create a standalone .fxpack in <gameDir>/photon/fxpacks for Discord or another file-sharing service. */
    public static void exportLocal() {
        if (!EXPORTING.compareAndSet(false, true)) {
            feedback("Photon: an LDLib2 resource export is already running.");
            return;
        }
        feedback("Photon: packaging all of ldlib2/assets into a shareable .fxpack…");
        CompletableFuture.runAsync(() -> {
            Path archive = null;
            try {
                archive = createArchive();
                var hash = sha256(archive);
                var outputDirectory = FXPacks.getFxPacksDir().toPath();
                Files.createDirectories(outputDirectory);
                var output = outputDirectory.resolve("ldlib2-assets-" + hash.substring(0, 16) + FXPacks.SUFFIX);
                if (Files.exists(output)) {
                    Files.deleteIfExists(archive);
                    archive = null;
                } else {
                    Files.move(archive, output);
                    archive = null;
                }
                feedback("Photon: share this file with your friend: " + output.toAbsolutePath());
            } catch (Exception failure) {
                Photon.LOGGER.warn("Could not export the local LDLib2 assets", failure);
                feedback("Photon: local export failed — " + safeMessage(failure));
            } finally {
                deleteTemporary(archive);
                EXPORTING.set(false);
            }
        });
    }

    private static void upload(FxPackSync.UploadPromptPacket prompt) {
        Path archive = null;
        try {
            if (activeToken != prompt.token()) return;
            archive = createArchive();
            var size = Files.size(archive);
            var hash = sha256(archive);
            if (activeToken != prompt.token()) return;
            PhotonNetworking.CHANNEL.sendToServer(new FxPackSync.UploadStartPacket(prompt.token(), size, hash));
            try (var input = Files.newInputStream(archive)) {
                byte[] buffer = new byte[FxPackSync.CHUNK_BYTES];
                long offset = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (activeToken != prompt.token()) return;
                    if (read == 0) continue;
                    var chunk = java.util.Arrays.copyOf(buffer, read);
                    PhotonNetworking.CHANNEL.sendToServer(new FxPackSync.UploadChunkPacket(prompt.token(), offset, chunk));
                    offset += read;
                }
            }
            feedback("Photon: uploading " + String.format(java.util.Locale.ROOT, "%.1f MiB", size / 1048576.0)
                    + " to the server for " + prompt.recipientName() + "…");
        } catch (Exception failure) {
            Photon.LOGGER.warn("Could not package or upload the local LDLib2 assets", failure);
            if (activeToken == prompt.token()) {
                feedback("Photon: resource upload failed — " + safeMessage(failure));
                UPLOADING.set(false);
                activeToken = -1;
            }
        } finally {
            deleteTemporary(archive);
        }
    }

    private static Path createArchive() throws IOException {
        var gamePath = Platform.getGamePath();
        var assets = gamePath.resolve("ldlib2").resolve("assets").normalize();
        if (!Files.isDirectory(assets, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("<gameDir>/ldlib2/assets was not found");
        }
        var archive = Files.createTempFile("photon-ldlib2-assets-", ".fxpack");
        try {
            long expandedBytes = 0;
            int entries = 0;
            try (var paths = Files.walk(assets);
                 var output = new ZipOutputStream(Files.newOutputStream(archive))) {
                output.putNextEntry(new ZipEntry("pack.mcmeta"));
                output.write("{\"pack\":{\"description\":\"LDLib2 shared resources\",\"pack_format\":15}}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
                for (var file : paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                    if (Files.isSymbolicLink(file)) continue;
                    if (++entries > MAX_ENTRIES) throw new IOException("ldlib2/assets has too many files to transfer");
                    var size = Files.size(file);
                    if (size < 0 || expandedBytes + size > MAX_EXPANDED_BYTES) {
                        throw new IOException("ldlib2/assets exceeds the 2 GiB expanded resource limit");
                    }
                    expandedBytes += size;
                    var relative = assets.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
                    var zipEntry = new ZipEntry("assets/" + relative);
                    zipEntry.setTime(0L);
                    output.putNextEntry(zipEntry);
                    try (var input = Files.newInputStream(file)) {
                        input.transferTo(output);
                    }
                    output.closeEntry();
                    if (Files.size(archive) > MAX_ARCHIVE_BYTES) {
                        throw new IOException("The compressed resource pack exceeds 512 MiB");
                    }
                }
            }
            var size = Files.size(archive);
            if (size <= 0 || size > MAX_ARCHIVE_BYTES || !FxPackSync.isValidFxPack(archive)) {
                throw new IOException("The assets folder has no valid FX, or its archive failed validation");
            }
            return archive;
        } catch (IOException | RuntimeException failure) {
            deleteTemporary(archive);
            throw failure;
        }
    }

    public static void status(FxPackSync.UploadStatusPacket status) {
        UPLOADING.set(false);
        activeToken = -1;
        feedback("Photon: " + status.message());
    }

    public static void onClientLogout() {
        UPLOADING.set(false);
        activeToken = -1;
    }

    private static String sha256(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java", impossible);
        }
    }

    private static void deleteTemporary(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException failure) {
            Photon.LOGGER.debug("Could not remove temporary LDLib2 resource archive {}", path, failure);
        }
    }

    private static String safeMessage(Exception failure) {
        var message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static void feedback(String message) {
        Minecraft.getInstance().execute(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null) player.sendSystemMessage(Component.literal(message));
        });
    }
}
