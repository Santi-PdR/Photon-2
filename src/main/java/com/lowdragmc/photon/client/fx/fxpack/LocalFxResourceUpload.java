package com.lowdragmc.photon.client.fx.fxpack;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonNetworking;
import com.lowdragmc.photon.network.FxPackSync;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
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
    private static volatile FxPackSync.UploadPromptPacket pendingUploadPrompt;
    private static final AtomicBoolean EXPORT_CONFIRMATION_PENDING = new AtomicBoolean();
    private static final int MAX_ENTRIES = 20_000;
    private static final long MAX_ARCHIVE_BYTES = FxPackSync.MAX_PACK_BYTES;
    private static final long MAX_EXPANDED_BYTES = FxPackSync.MAX_EXPANDED_PACK_BYTES;

    private LocalFxResourceUpload() {
    }

    /** Ask the player before reading any local assets in response to a server upload request. */
    public static void begin(FxPackSync.UploadPromptPacket prompt) {
        pendingUploadPrompt = prompt;
        feedbackWithActions(Component.translatable("photon.fxpack.local.upload_prompt", prompt.recipientName()),
                "/photon_client confirm_ldlib2_upload " + prompt.token(), Component.translatable("photon.fxpack.local.confirm_upload"),
                "/photon_client cancel_ldlib2_upload " + prompt.token(), Component.translatable("photon.fxpack.local.cancel"));
    }

    public static void confirmUpload(long token) {
        var prompt = pendingUploadPrompt;
        if (prompt == null || prompt.token() != token) {
            feedback(Component.translatable("photon.fxpack.local.upload_expired"));
            return;
        }
        pendingUploadPrompt = null;
        if (!UPLOADING.compareAndSet(false, true)) {
            feedback(Component.translatable("photon.fxpack.local.upload_busy"));
            return;
        }
        activeToken = prompt.token();
        feedback(Component.translatable("photon.fxpack.local.packaging_for", prompt.recipientName()));
        CompletableFuture.runAsync(() -> upload(prompt));
    }

    public static void cancelUpload(long token) {
        var prompt = pendingUploadPrompt;
        if (prompt != null && prompt.token() == token) {
            pendingUploadPrompt = null;
            feedback(Component.translatable("photon.fxpack.local.upload_cancelled"));
        }
    }

    /** Ask for explicit confirmation before creating a standalone shareable .fxpack. */
    public static void exportLocal() {
        if (!EXPORT_CONFIRMATION_PENDING.compareAndSet(false, true)) {
            feedback(Component.translatable("photon.fxpack.local.export_pending"));
            return;
        }
        feedbackWithActions(Component.translatable("photon.fxpack.local.export_prompt"),
                "/photon_client confirm_export_ldlib2_assets", Component.translatable("photon.fxpack.local.confirm_export"),
                "/photon_client cancel_export_ldlib2_assets", Component.translatable("photon.fxpack.local.cancel"));
    }

    public static void cancelLocalExport() {
        if (EXPORT_CONFIRMATION_PENDING.compareAndSet(true, false)) {
            feedback(Component.translatable("photon.fxpack.local.export_cancelled"));
        }
    }

    public static void confirmLocalExport() {
        if (!EXPORT_CONFIRMATION_PENDING.compareAndSet(true, false)) {
            feedback(Component.translatable("photon.fxpack.local.export_none_pending"));
            return;
        }
        if (!EXPORTING.compareAndSet(false, true)) {
            feedback(Component.translatable("photon.fxpack.local.export_running"));
            return;
        }
        feedback(Component.translatable("photon.fxpack.local.export_packaging"));
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
                feedback(Component.translatable("photon.fxpack.local.export_done", output.toAbsolutePath().toString()));
            } catch (Exception failure) {
                Photon.LOGGER.warn("Could not export the local LDLib2 assets", failure);
                feedback(Component.translatable("photon.fxpack.local.export_failed", safeMessage(failure)));
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
            feedback(Component.translatable("photon.fxpack.local.uploading",
                    String.format(java.util.Locale.ROOT, "%.1f", size / 1048576.0), prompt.recipientName()));
        } catch (Exception failure) {
            Photon.LOGGER.warn("Could not package or upload the local LDLib2 assets", failure);
            if (activeToken == prompt.token()) {
                feedback(Component.translatable("photon.fxpack.local.upload_failed", safeMessage(failure)));
                UPLOADING.set(false);
                activeToken = -1;
            }
        } finally {
            deleteTemporary(archive);
        }
    }

    /** Package-private for the upload-archive regression; the flow calls it after a chat confirmation. */
    static Path createArchive() throws IOException {
        var gamePath = Platform.getGamePath();
        var assets = gamePath.resolve("ldlib2").resolve("assets").normalize();
        if (!Files.isDirectory(assets, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("<gameDir>/ldlib2/assets was not found");
        }
        var archive = Files.createTempFile("photon-ldlib2-assets-", ".fxpack");
        try {
            long expandedBytes = 0;
            int entries = 0;
            int skipped = 0;
            try (var paths = Files.walk(assets);
                 var output = new ZipOutputStream(Files.newOutputStream(archive))) {
                output.putNextEntry(new ZipEntry("pack.mcmeta"));
                output.write("{\"pack\":{\"description\":\"LDLib2 shared resources\",\"pack_format\":15}}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
                for (var file : paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                    if (Files.isSymbolicLink(file)) continue;
                    var relative = assets.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
                    // server-side pack validation rejects unsafe ZIP entry names (backslashes, colons, control
                    // characters); one oddly named file must not fail the whole transfer with a misleading error
                    if (!isSafeEntryName(relative)) {
                        skipped++;
                        Photon.LOGGER.warn("ldlib2/assets: skipping {} — its name cannot travel in an .fxpack", file);
                        continue;
                    }
                    if (++entries > MAX_ENTRIES) throw new IOException("ldlib2/assets has too many files to transfer");
                    var size = Files.size(file);
                    if (size < 0 || expandedBytes + size > MAX_EXPANDED_BYTES) {
                        throw new IOException("ldlib2/assets exceeds the 2 GiB expanded resource limit");
                    }
                    expandedBytes += size;
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
                throw new IOException("The assets folder has no valid FX, or its archive failed validation"
                        + (skipped > 0 ? " (" + skipped + " file(s) with unsafe names were skipped; see the log)" : ""));
            }
            return archive;
        } catch (IOException | RuntimeException failure) {
            deleteTemporary(archive);
            throw failure;
        }
    }

    /** Mirror of {@code FxPackSync.isSafeZipEntryName} for a relative path about to become {@code assets/<path>}. */
    static boolean isSafeEntryName(String relative) {
        if (relative == null || relative.isBlank() || relative.indexOf('\\') >= 0 || relative.indexOf(':') >= 0
                || relative.chars().anyMatch(ch -> ch == 0 || ch < 0x20 || ch == 0x7f)) return false;
        for (var segment : relative.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return false;
        }
        return true;
    }

    public static void status(FxPackSync.UploadStatusPacket status) {
        UPLOADING.set(false);
        activeToken = -1;
        feedback(Component.translatable("photon.fxpack.upload.status", status.message()));
    }

    public static void onClientLogout() {
        UPLOADING.set(false);
        activeToken = -1;
        pendingUploadPrompt = null;
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

    private static void feedbackWithActions(MutableComponent message, String acceptCommand, MutableComponent acceptLabel,
                                            String rejectCommand, MutableComponent rejectLabel) {
        Minecraft.getInstance().execute(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            var accept = acceptLabel.withStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, acceptCommand))
                    .withUnderlined(true));
            var reject = rejectLabel.withStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, rejectCommand))
                    .withUnderlined(true));
            player.sendSystemMessage(message.append(accept).append(" ").append(reject));
        });
    }

    private static void feedback(Component message) {
        Minecraft.getInstance().execute(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null) player.sendSystemMessage(message);
        });
    }
}
