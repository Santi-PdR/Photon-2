package com.lowdragmc.photon.network;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonNetworking;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

/** Server-to-client delivery of public FX packs and recipient-private LDLib2 resource packs. */
public final class FxPackSync {
    public static final String PACK_DIRECTORY = "photon/fxpacks";
    public static final String PRIVATE_PACK_DIRECTORY = "photon/private-fxpacks";
    public static final String SUFFIX = ".fxpack";
    public static final int CHUNK_BYTES = 24 * 1024;
    /** Large-workspace ceiling for one compressed pack transfer. */
    public static final long MAX_PACK_BYTES = 512L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 3L * 1024 * 1024 * 1024;
    public static final long MAX_EXPANDED_PACK_BYTES = 2L * 1024 * 1024 * 1024;
    /** Bound aggregate decompression work even when a manifest contains many small compressed archives. */
    public static final long MAX_TOTAL_EXPANDED_PACK_BYTES = 8L * 1024 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 20_000;
    private static final int MAX_PACK_CANDIDATES = 4_096;
    private static final Pattern FX_ENTRY = Pattern.compile("assets/[^/]+/fx/.+\\.fx");
    private static final int MAX_SERVER_PACKS = 32;
    private static final int MAX_PRIVATE_PACKS = 16;
    private static final int MAX_PACKS = MAX_SERVER_PACKS + MAX_PRIVATE_PACKS;
    private static final long MAX_PRIVATE_PACK_BYTES = 2L * 1024 * 1024 * 1024;
    private static final long MAX_SERVER_PACK_BYTES = 512L * 1024 * 1024;
    private static final long REFRESH_COOLDOWN_NANOS = 5_000_000_000L;
    private static final Map<UUID, Map<String, PackEntry>> OFFERED = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Path>> OFFERED_FILES = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> NEXT_CHUNK_OFFSETS = new ConcurrentHashMap<>();
    private static final Map<UUID, UploadRequest> UPLOAD_REQUESTS = new ConcurrentHashMap<>();
    private static final Map<UUID, UploadState> UPLOADS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_REFRESH_NANOS = new ConcurrentHashMap<>();
    /** Token of the manifest currently accepted for chunk requests. */
    private static final Map<UUID, Long> OFFER_TOKENS = new ConcurrentHashMap<>();
    /** Token of the newest scan request; an unfinished scan must not invalidate a working transfer. */
    private static final Map<UUID, Long> LATEST_OFFER_TOKENS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong NEXT_OFFER_TOKEN =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong NEXT_UPLOAD_TOKEN =
            new java.util.concurrent.atomic.AtomicLong();
    /** Validated expansion sizes keyed by content hash, so repeat offers avoid decompressing unchanged packs. */
    private static final Map<String, Long> VALIDATED_PACK_EXPANSIONS = new ConcurrentHashMap<>();
    /**
     * Content hashes keyed by {@code path|size|mtime}, so per-login offers do not rehash unchanged private packs.
     * Private pack files are content-addressed ({@code <sha256>.fxpack}) and never rewritten in place, so the only
     * way this entry goes stale is a same-size edit inside one millisecond — the manifest hash is then re-checked
     * against the file on the client before use anyway.
     */
    private static final Map<String, String> PRIVATE_HASH_CACHE = new ConcurrentHashMap<>();
    /** Concurrent login/refresh offers share one disk scan for a server. */
    private static MinecraftServer scanServer;
    private static CompletableFuture<List<PackEntry>> activeScan;

    private FxPackSync() {}

    public static void onPlayerLogin(ServerPlayer player) {
        offer(player, false);
    }

    /**
     * Send one player the current pack list again, on their request. The manifest is otherwise sent only at login, so
     * a pack uploaded while somebody was already online was invisible to them until they reconnected. Re-sending a
     * manifest is safe: the client compares hashes and skips whatever it already has.
     */
    public static void onRefreshRequest(ServerPlayer player) {
        offer(player, true);
    }

    /** Ask this player's client to upload its local {@code <gameDir>/ldlib2/assets} tree for one recipient. */
    public static boolean beginLocalResourceUpload(ServerPlayer sender, ServerPlayer recipient) {
        if (sender == null || recipient == null || sender.getServer() == null
                || sender.getServer() != recipient.getServer()) return false;
        var token = NEXT_UPLOAD_TOKEN.incrementAndGet();
        UPLOAD_REQUESTS.put(sender.getUUID(), new UploadRequest(token, recipient.getUUID(), System.nanoTime()));
        deleteUpload(UPLOADS.remove(sender.getUUID()));
        PhotonNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                new UploadPromptPacket(token, recipient.getUUID(), recipient.getGameProfile().getName()));
        return true;
    }

    public static void handleUploadStart(UploadStartPacket packet, ServerPlayer sender) {
        if (sender == null) return;
        if (packet.token() < 0 || packet.size() <= 0 || packet.size() > MAX_PACK_BYTES
                || !packet.sha256().matches("[0-9a-f]{64}")) {
            sendUploadStatus(sender, false, "The upload metadata is invalid or exceeds 512 MiB.");
            return;
        }
        var request = UPLOAD_REQUESTS.get(sender.getUUID());
        if (request == null || request.token != packet.token()) return;
        if (System.nanoTime() - request.createdAtNanos > java.util.concurrent.TimeUnit.MINUTES.toNanos(10)) {
            UPLOAD_REQUESTS.remove(sender.getUUID(), request);
            sendUploadStatus(sender, false, "The transfer took too long to prepare; run the send command again.");
            return;
        }
        var server = sender.getServer();
        var target = server == null ? null : server.getPlayerList().getPlayer(request.recipient);
        if (target == null) {
            UPLOAD_REQUESTS.remove(sender.getUUID(), request);
            sendUploadStatus(sender, false, "Recipient is no longer online.");
            return;
        }
        var tempDirectory = server.getServerDirectory().toPath().resolve(PRIVATE_PACK_DIRECTORY).resolve(".incoming");
        var temp = tempDirectory.resolve(sender.getUUID() + "-" + packet.token() + ".part").normalize();
        try {
            Files.createDirectories(tempDirectory);
            if (!temp.getParent().equals(tempDirectory.normalize())) return;
            try (var file = new RandomAccessFile(temp.toFile(), "rw")) {
                file.setLength(0);
            }
            UPLOAD_REQUESTS.remove(sender.getUUID(), request);
            var old = UPLOADS.put(sender.getUUID(), new UploadState(packet.token(), request.recipient,
                    packet.size(), packet.sha256(), temp));
            deleteUpload(old);
        } catch (IOException | RuntimeException failure) {
            UPLOAD_REQUESTS.remove(sender.getUUID(), request);
            deleteUpload(new UploadState(packet.token(), request.recipient, packet.size(), packet.sha256(), temp));
            sendUploadStatus(sender, false, "Could not prepare the server upload file.");
            Photon.LOGGER.warn("Could not prepare private Photon FX pack upload from {}", sender.getGameProfile().getName(), failure);
        }
    }

    public static void handleUploadChunk(UploadChunkPacket packet, ServerPlayer sender) {
        if (sender == null || packet.bytes().length == 0 || packet.bytes().length > CHUNK_BYTES) return;
        var state = UPLOADS.get(sender.getUUID());
        if (state == null || packet.token() != state.token) return;
        if (packet.offset() != state.received || packet.bytes().length > state.size - state.received) {
            UPLOADS.remove(sender.getUUID(), state);
            deleteUpload(state);
            sendUploadStatus(sender, false, "The resource upload arrived out of order; please retry.");
            return;
        }
        try (var file = new RandomAccessFile(state.temp.toFile(), "rw")) {
            file.seek(state.received);
            file.write(packet.bytes());
            state.received += packet.bytes().length;
        } catch (IOException failure) {
            UPLOADS.remove(sender.getUUID(), state);
            deleteUpload(state);
            sendUploadStatus(sender, false, "The upload failed while writing data on the server.");
            Photon.LOGGER.warn("Could not receive private Photon FX pack from {}", sender.getGameProfile().getName(), failure);
            return;
        }
        if (state.received == state.size) completeUpload(sender, state);
    }

    private static void completeUpload(ServerPlayer sender, UploadState state) {
        UPLOADS.remove(sender.getUUID(), state);
        var server = sender.getServer();
        var recipient = server == null ? null : server.getPlayerList().getPlayer(state.recipient);
        try {
            if (Files.size(state.temp) != state.size || !sha256(state.temp).equals(state.sha256)
                    || !inspectFxPack(state.temp, MAX_EXPANDED_PACK_BYTES).valid()) {
                sendUploadStatus(sender, false, "The archive is invalid or does not contain a Photon FX.");
                return;
            }
            if (recipient == null) {
                sendUploadStatus(sender, false, "Recipient went offline before the transfer completed.");
                return;
            }
            var directory = server.getServerDirectory().toPath().resolve(PRIVATE_PACK_DIRECTORY)
                    .resolve(state.recipient.toString()).normalize();
            Files.createDirectories(directory);
            var target = directory.resolve(state.sha256 + SUFFIX).normalize();
            if (!target.getParent().equals(directory)) throw new IOException("Invalid private pack target");
            boolean reusable = Files.isRegularFile(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    && Files.size(target) == state.size && sha256(target).equals(state.sha256);
            if (!reusable) {
                long existingBytes = 0;
                int existingFiles = 0;
                try (var files = Files.list(directory)) {
                    for (var file : files.filter(path -> !path.equals(target)
                            && Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)).toList()) {
                        existingFiles++;
                        existingBytes += Files.size(file);
                    }
                }
                if (existingFiles >= MAX_PRIVATE_PACKS || existingBytes + state.size > MAX_PRIVATE_PACK_BYTES) {
                    sendUploadStatus(sender, false, "That player's private FX pack storage is full.");
                    return;
                }
                Files.deleteIfExists(target);
                try {
                    Files.move(state.temp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(state.temp, target);
                }
            }
            Photon.LOGGER.info("Stored private Photon FX pack {} for {} from {}", state.sha256,
                    recipient.getGameProfile().getName(), sender.getGameProfile().getName());
            sendUploadStatus(sender, true, "Sent your ldlib2/assets resources to "
                    + recipient.getGameProfile().getName() + ". They will reload automatically.");
            recipient.sendSystemMessage(net.minecraft.network.chat.Component.literal("Photon: received FX/resources from "
                    + sender.getGameProfile().getName() + "; reloading assets."));
            offer(recipient, false);
        } catch (IOException | RuntimeException failure) {
            sendUploadStatus(sender, false, "Could not validate or store the resource pack on the server.");
            Photon.LOGGER.warn("Could not finalize private Photon FX pack upload", failure);
        } finally {
            deleteUpload(state);
        }
    }

    private static void sendUploadStatus(ServerPlayer player, boolean success, String message) {
        PhotonNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new UploadStatusPacket(success, message));
    }

    private static void deleteUpload(@Nullable UploadState state) {
        if (state == null) return;
        try {
            Files.deleteIfExists(state.temp);
        } catch (IOException | RuntimeException failure) {
            Photon.LOGGER.warn("Could not remove temporary Photon FX upload {}", state.temp, failure);
        }
    }

    private static void offer(ServerPlayer player, boolean requested) {
        if (requested) {
            var now = System.nanoTime();
            var allowed = new java.util.concurrent.atomic.AtomicBoolean();
            LAST_REFRESH_NANOS.compute(player.getUUID(), (uuid, previous) -> {
                if (previous == null || now - previous >= REFRESH_COOLDOWN_NANOS) {
                    allowed.set(true);
                    return now;
                }
                return previous;
            });
            if (!allowed.get()) return;
        }
        var server = player.getServer();
        if (server == null) return;
        var playerId = player.getUUID();
        var offerToken = NEXT_OFFER_TOKEN.incrementAndGet();
        LATEST_OFFER_TOKENS.put(playerId, offerToken);
        scanAsync(server).whenComplete((entries, failure) -> {
            long globalExpanded = failure == null ? entries.stream()
                    .mapToLong(entry -> VALIDATED_PACK_EXPANSIONS.getOrDefault(entry.sha256(), 0L)).sum() : 0;
            var personalPacks = failure == null
                    ? scanPrivatePacks(server, playerId, Math.max(0, MAX_TOTAL_EXPANDED_PACK_BYTES - globalExpanded))
                    : List.<PrivatePack>of();
            try {
                server.execute(() -> {
                    if (server.getPlayerList().getPlayer(playerId) != player
                            || !Objects.equals(LATEST_OFFER_TOKENS.get(playerId), offerToken)) return;
                    if (failure != null) {
                        Photon.LOGGER.error("Could not scan server Photon FX packs", failure);
                        return;
                    }
                    var allEntries = new ArrayList<PackEntry>(entries);
                    var offered = new ConcurrentHashMap<String, PackEntry>();
                    var filesByHash = new ConcurrentHashMap<String, Path>();
                    var globalDirectory = server.getServerDirectory().toPath().resolve(PACK_DIRECTORY).normalize();
                    entries.forEach(entry -> {
                        offered.put(entry.sha256(), entry);
                        filesByHash.put(entry.sha256(), globalDirectory.resolve(entry.fileName()).normalize());
                    });
                    for (var personal : personalPacks) {
                        if (offered.putIfAbsent(personal.entry.sha256(), personal.entry) == null) {
                            allEntries.add(personal.entry);
                            filesByHash.put(personal.entry.sha256(), personal.path);
                        }
                    }
                    var nextOffsets = new ConcurrentHashMap<String, Long>();
                    allEntries.forEach(entry -> nextOffsets.put(entry.sha256(), 0L));
                    PhotonNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new ManifestPacket(offerToken, allEntries));
                    OFFERED.put(playerId, Map.copyOf(offered));
                    OFFERED_FILES.put(playerId, Map.copyOf(filesByHash));
                    NEXT_CHUNK_OFFSETS.put(playerId, nextOffsets);
                    OFFER_TOKENS.put(playerId, offerToken);
                    Photon.LOGGER.info("{} {} Photon FX pack(s) to {}", requested ? "Re-offered" : "Offered",
                            allEntries.size(), player.getGameProfile().getName());
                });
            } catch (RuntimeException schedulingFailure) {
                Photon.LOGGER.warn("Could not queue Photon FX pack manifest for {}", playerId, schedulingFailure);
            }
        });
    }

    private static synchronized CompletableFuture<List<PackEntry>> scanAsync(MinecraftServer server) {
        if (activeScan != null && scanServer == server && !activeScan.isDone()) return activeScan;
        scanServer = server;
        var scan = CompletableFuture.supplyAsync(() -> scan(server), Util.backgroundExecutor());
        activeScan = scan;
        scan.whenComplete((entries, failure) -> {
            synchronized (FxPackSync.class) {
                if (activeScan == scan) {
                    activeScan = null;
                    scanServer = null;
                }
            }
        });
        return scan;
    }

    public static void onPlayerLogout(ServerPlayer player) {
        OFFERED.remove(player.getUUID());
        OFFERED_FILES.remove(player.getUUID());
        NEXT_CHUNK_OFFSETS.remove(player.getUUID());
        UPLOAD_REQUESTS.remove(player.getUUID());
        deleteUpload(UPLOADS.remove(player.getUUID()));
        LAST_REFRESH_NANOS.remove(player.getUUID());
        OFFER_TOKENS.remove(player.getUUID());
        LATEST_OFFER_TOKENS.remove(player.getUUID());
    }

    public static void onServerStopping() {
        OFFERED.clear();
        OFFERED_FILES.clear();
        NEXT_CHUNK_OFFSETS.clear();
        UPLOAD_REQUESTS.clear();
        UPLOADS.values().forEach(FxPackSync::deleteUpload);
        UPLOADS.clear();
        LAST_REFRESH_NANOS.clear();
        OFFER_TOKENS.clear();
        LATEST_OFFER_TOKENS.clear();
        PRIVATE_HASH_CACHE.clear();
        synchronized (FxPackSync.class) {
            activeScan = null;
            scanServer = null;
        }
    }

    /**
     * Drop partial uploads a crash left behind in {@code .incoming}: nothing can be in flight at startup, and each
     * stale {@code .part} file can hold up to a full pack of disk space forever otherwise.
     */
    public static void onServerStarting(MinecraftServer server) {
        PRIVATE_HASH_CACHE.clear();
        var incoming = server.getServerDirectory().toPath().resolve(PRIVATE_PACK_DIRECTORY).resolve(".incoming");
        if (!Files.isDirectory(incoming)) return;
        try (var files = Files.list(incoming)) {
            for (var path : files.filter(file -> Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    && file.getFileName().toString().endsWith(".part")).toList()) {
                try {
                    if (Files.deleteIfExists(path)) {
                        Photon.LOGGER.info("Removed leftover Photon FX upload part {}", path.getFileName());
                    }
                } catch (IOException | RuntimeException failure) {
                    Photon.LOGGER.warn("Could not remove leftover Photon FX upload part {}", path, failure);
                }
            }
        } catch (IOException | RuntimeException failure) {
            Photon.LOGGER.warn("Could not clean leftover Photon FX upload parts in {}", incoming, failure);
        }
    }

    /** Bounded disk and ZIP inspection; called from the background executor via {@link #scanAsync}. */
    private static List<PackEntry> scan(MinecraftServer server) {
        var directory = server.getServerDirectory().toPath().resolve(PACK_DIRECTORY);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new java.util.concurrent.CompletionException(
                    "Could not create Photon FX pack directory " + directory, e);
        }
        var result = new ArrayList<PackEntry>();
        var seenHashes = new HashSet<String>();
        long totalBytes = 0;
        long scannedBytes = 0;
        long totalExpandedBytes = 0;
        try (var files = Files.list(directory)) {
            var candidates = files.filter(file -> Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    .filter(file -> isSafePackFileName(file.getFileName().toString()))
                    .limit(MAX_PACK_CANDIDATES + 1L).sorted().toList();
            if (candidates.size() > MAX_PACK_CANDIDATES) {
                Photon.LOGGER.warn("Photon FX pack scan limited to {} candidate files", MAX_PACK_CANDIDATES);
            }
            for (var path : candidates.stream().limit(MAX_PACK_CANDIDATES).toList()) {
                try {
                    if (result.size() >= MAX_SERVER_PACKS) break;
                    var size = Files.size(path);
                    if (size <= 0 || size > MAX_PACK_BYTES || totalBytes + size > MAX_SERVER_PACK_BYTES) {
                        Photon.LOGGER.warn("Photon FX sync skipped {} (size {} exceeds limits)", path.getFileName(), size);
                        continue;
                    }
                    if (scannedBytes > MAX_SERVER_PACK_BYTES - size) {
                        Photon.LOGGER.warn("Photon FX sync stopped scanning after reaching the pack validation byte budget");
                        break;
                    }
                    scannedBytes += size;
                    var hash = sha256(path);
                    if (!seenHashes.add(hash)) continue;
                    var expandedBytes = VALIDATED_PACK_EXPANSIONS.get(hash);
                    if (expandedBytes == null) {
                        var remainingBudget = MAX_TOTAL_EXPANDED_PACK_BYTES - totalExpandedBytes;
                        if (remainingBudget <= 0) break;
                        var validation = inspectFxPack(path, Math.min(MAX_EXPANDED_PACK_BYTES, remainingBudget));
                        totalExpandedBytes += validation.expandedBytes();
                        if (!validation.valid() || !hash.equals(sha256(path))) {
                            Photon.LOGGER.warn("Photon FX sync skipped invalid or concurrently changed pack {}", path.getFileName());
                            if (totalExpandedBytes >= MAX_TOTAL_EXPANDED_PACK_BYTES) break;
                            continue;
                        }
                        expandedBytes = validation.expandedBytes();
                        var previous = VALIDATED_PACK_EXPANSIONS.putIfAbsent(hash, expandedBytes);
                        if (previous != null) expandedBytes = previous;
                        if (VALIDATED_PACK_EXPANSIONS.size() > 512) VALIDATED_PACK_EXPANSIONS.clear();
                    } else {
                        if (totalExpandedBytes > MAX_TOTAL_EXPANDED_PACK_BYTES - expandedBytes) {
                            Photon.LOGGER.warn("Photon FX sync skipped {} (aggregate expanded pack data exceeds limit)",
                                    path.getFileName());
                            continue;
                        }
                        totalExpandedBytes += expandedBytes;
                    }
                    result.add(new PackEntry(path.getFileName().toString(), size, hash));
                    totalBytes += size;
                } catch (IOException e) {
                    Photon.LOGGER.warn("Photon FX sync could not read pack {}; skipping it", path.getFileName(), e);
                }
            }
        } catch (IOException e) {
            throw new java.util.concurrent.CompletionException(
                    "Could not scan server Photon FX packs in " + directory, e);
        }
        return List.copyOf(result);
    }

    /** Only the addressed recipient's private packs are added to their manifest; they never enter the public directory. */
    private static List<PrivatePack> scanPrivatePacks(MinecraftServer server, UUID recipient, long expandedBudget) {
        var directory = server.getServerDirectory().toPath().resolve(PRIVATE_PACK_DIRECTORY)
                .resolve(recipient.toString()).normalize();
        if (!Files.isDirectory(directory)) return List.of();
        // the manifest budget is shared with the public packs already scanned: private work may not exceed it
        var budget = Math.min(Math.max(0, expandedBudget), MAX_TOTAL_EXPANDED_PACK_BYTES);
        var result = new ArrayList<PrivatePack>();
        long total = 0;
        long expandedTotal = 0;
        try (var files = Files.list(directory)) {
            for (var path : files.filter(file -> Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    .filter(file -> file.getFileName().toString().endsWith(SUFFIX)).sorted().toList()) {
                if (result.size() >= MAX_PRIVATE_PACKS) break;
                var size = Files.size(path);
                if (size <= 0 || size > MAX_PACK_BYTES || total + size > MAX_PRIVATE_PACK_BYTES) continue;
                var hash = cachedSha256(path, size);
                // unchanged packs keep their validated expansion size: a login/respawn/dimension change must not
                // re-decompress up to 2 GiB per recipient on the background executor
                var expandedBytes = VALIDATED_PACK_EXPANSIONS.get(hash);
                if (expandedBytes == null) {
                    var remainingExpanded = budget - expandedTotal;
                    if (remainingExpanded <= 0) break;
                    var validation = inspectFxPack(path, Math.min(MAX_EXPANDED_PACK_BYTES, remainingExpanded));
                    if (!validation.valid()) continue;
                    expandedBytes = validation.expandedBytes();
                    var previous = VALIDATED_PACK_EXPANSIONS.putIfAbsent(hash, expandedBytes);
                    if (previous != null) expandedBytes = previous;
                    if (VALIDATED_PACK_EXPANSIONS.size() > 512) VALIDATED_PACK_EXPANSIONS.clear();
                }
                if (expandedTotal > budget - expandedBytes) continue;
                expandedTotal += expandedBytes;
                total += size;
                result.add(new PrivatePack(new PackEntry("private-" + hash + SUFFIX, size, hash), path));
            }
        } catch (IOException | RuntimeException failure) {
            Photon.LOGGER.warn("Could not scan private Photon FX packs for {}", recipient, failure);
        }
        return List.copyOf(result);
    }

    /** {@link #sha256} behind a size+mtime memo; every caller already bounds how many files it scans. */
    private static String cachedSha256(Path path, long size) throws IOException {
        var modified = Files.getLastModifiedTime(path).toMillis();
        var key = path + "|" + size + "|" + modified;
        var cached = PRIVATE_HASH_CACHE.get(key);
        if (cached != null) return cached;
        var hash = sha256(path);
        if (PRIVATE_HASH_CACHE.size() > 512) PRIVATE_HASH_CACHE.clear();
        PRIVATE_HASH_CACHE.put(key, hash);
        return hash;
    }

    /** Result of streaming an untrusted archive; {@code expandedBytes} is actual work already performed. */
    public record FxPackValidation(boolean valid, long expandedBytes) {}

    /** Validate an untrusted pack without trusting ZIP central-directory sizes. */
    public static boolean isValidFxPack(Path path) {
        return inspectFxPack(path, MAX_EXPANDED_PACK_BYTES).valid();
    }

    /** Return the actual expanded byte count, or {@code -1} when the pack is invalid. */
    public static long expandedFxPackBytes(Path path, long maxExpandedBytes) {
        var validation = inspectFxPack(path, maxExpandedBytes);
        return validation.valid() ? validation.expandedBytes() : -1;
    }

    /**
     * Stream and validate a pack against a caller-supplied byte budget (plus at most one byte to detect overflow).
     * For invalid archives, the result still reports how many bytes were expanded before rejection, allowing callers
     * validating multiple packs to enforce a global work limit even when an archive fails late in its contents.
     */
    public static FxPackValidation inspectFxPack(Path path, long maxExpandedBytes) {
        if (path == null || maxExpandedBytes <= 0) return new FxPackValidation(false, 0);
        long expanded = 0;
        try (var zip = new ZipFile(path.toFile())) {
            var entries = zip.entries();
            var names = new HashSet<String>();
            var buffer = new byte[16 * 1024];
            int count = 0;
            boolean hasFx = false;
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var name = entry.getName();
                if (++count > MAX_ZIP_ENTRIES || !isSafeZipEntryName(name) || !names.add(name)) {
                    return new FxPackValidation(false, expanded);
                }
                if (!entry.isDirectory()) {
                    if (FX_ENTRY.matcher(name).matches()) hasFx = true;
                    try (var input = zip.getInputStream(entry)) {
                        int read;
                        while (true) {
                            var remaining = maxExpandedBytes - expanded;
                            // At most probe one byte past the limit, so an archive exactly on the boundary remains
                            // valid while decompression work cannot overshoot the caller's budget by a whole buffer.
                            var request = remaining < buffer.length ? (int) remaining + 1 : buffer.length;
                            read = input.read(buffer, 0, request);
                            if (read < 0) break;
                            if (read == 0) continue;
                            if (read > remaining) {
                                return new FxPackValidation(false, expanded + read);
                            }
                            expanded += read;
                        }
                    }
                }
            }
            return new FxPackValidation(hasFx, expanded);
        } catch (IOException | RuntimeException e) {
            return new FxPackValidation(false, expanded);
        }
    }

    private static boolean isSafePackFileName(String name) {
        return name != null && !name.isBlank() && !name.equals(".") && !name.equals("..")
                && name.endsWith(SUFFIX) && name.length() <= 128
                && name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 128
                && !name.contains("/") && !name.contains("\\") && !name.contains(":")
                && name.chars().noneMatch(ch -> ch < 0x20 || ch == 0x7f);
    }

    private static boolean isSafeZipEntryName(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\")
                || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0
                || name.chars().anyMatch(ch -> ch == 0 || ch < 0x20 || ch == 0x7f)) return false;
        var path = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        if (path.isEmpty()) return false;
        for (var segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return false;
        }
        return true;
    }

    private static String sha256(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            var digest = newDigest();
            var buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by Java", e);
        }
    }

    public static void handleManifest(ManifestPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.lowdragmc.photon.client.fx.fxpack.ClientFxPackSync.begin(packet.requestToken(), packet.entries()));
    }

    public static void handleRequest(RequestPacket packet, ServerPlayer player) {
        if (player == null) return;
        if (packet.isRefresh()) {
            onRefreshRequest(player);
            return;
        }
        if (packet.requestToken() < 0 || packet.offset() < 0 || !packet.sha256().matches("[0-9a-f]{64}")) return;
        var uuid = player.getUUID();
        if (!Objects.equals(OFFER_TOKENS.get(uuid), packet.requestToken())) return;
        var entry = OFFERED.getOrDefault(uuid, Map.of()).get(packet.sha256());
        var nextOffsets = NEXT_CHUNK_OFFSETS.get(uuid);
        var expectedOffset = nextOffsets == null ? null : nextOffsets.get(packet.sha256());
        if (entry == null || expectedOffset == null || expectedOffset.longValue() != packet.offset()
                || packet.offset() >= entry.size()) return;
        var server = player.getServer();
        if (server == null) return;
        var path = OFFERED_FILES.getOrDefault(uuid, Map.of()).get(packet.sha256());
        if (path == null || !Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        try (var file = new RandomAccessFile(path.toFile(), "r")) {
            if (file.length() != entry.size()) return;
            file.seek(packet.offset());
            var bytes = new byte[(int) Math.min(CHUNK_BYTES, entry.size() - packet.offset())];
            file.readFully(bytes);
            if (!nextOffsets.replace(packet.sha256(), packet.offset(), packet.offset() + bytes.length)) return;
            PhotonNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new ChunkPacket(packet.requestToken(), entry.sha256(), packet.offset(), entry.size(), bytes));
        } catch (IOException e) {
            Photon.LOGGER.warn("Could not stream Photon FX pack {} to {}", entry.fileName(),
                    player.getGameProfile().getName(), e);
        }
    }

    /** Asks the server for the current pack list, for the client command, without the offset/chunk machinery. */
    public static RequestPacket refreshRequest() {
        return new RequestPacket(-1, "", -1);
    }

    /**
     * The manifest goes out at login, which for a player coming back from the nether or respawning is not "later": the
     * server may have received a new pack while they were in a loading screen. Re-offering on every (re)join costs one
     * directory scan and a hash list, and the client skips what it already has.
     */
    public static void onPlayerJoinLevel(ServerPlayer player) {
        offer(player, true);
    }

    public static void handleChunk(ChunkPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.lowdragmc.photon.client.fx.fxpack.ClientFxPackSync.accept(packet));
    }

    private record PrivatePack(PackEntry entry, Path path) {
    }

    private record UploadRequest(long token, UUID recipient, long createdAtNanos) {
    }

    private static final class UploadState {
        private final long token;
        private final UUID recipient;
        private final long size;
        private final String sha256;
        private final Path temp;
        private long received;

        private UploadState(long token, UUID recipient, long size, String sha256, Path temp) {
            this.token = token;
            this.recipient = recipient;
            this.size = size;
            this.sha256 = sha256;
            this.temp = temp;
        }
    }

    public static void handleUploadPrompt(UploadPromptPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.lowdragmc.photon.client.fx.fxpack.LocalFxResourceUpload.begin(packet));
    }

    public static void handleUploadStatus(UploadStatusPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.lowdragmc.photon.client.fx.fxpack.LocalFxResourceUpload.status(packet));
    }

    public record UploadPromptPacket(long token, UUID recipient, String recipientName) {
        public static void encode(UploadPromptPacket packet, FriendlyByteBuf buf) {
            buf.writeVarLong(packet.token());
            buf.writeUUID(packet.recipient());
            buf.writeUtf(packet.recipientName(), 64);
        }
        public static UploadPromptPacket decode(FriendlyByteBuf buf) {
            var token = buf.readVarLong();
            var recipient = buf.readUUID();
            var name = buf.readUtf(64);
            if (token < 0) throw new IllegalArgumentException("Invalid resource upload token");
            return new UploadPromptPacket(token, recipient, name);
        }
    }

    public record UploadStartPacket(long token, long size, String sha256) {
        public static void encode(UploadStartPacket packet, FriendlyByteBuf buf) {
            buf.writeVarLong(packet.token());
            buf.writeVarLong(packet.size());
            buf.writeUtf(packet.sha256(), 64);
        }
        public static UploadStartPacket decode(FriendlyByteBuf buf) {
            return new UploadStartPacket(buf.readVarLong(), buf.readVarLong(), buf.readUtf(64));
        }
    }

    public record UploadChunkPacket(long token, long offset, byte[] bytes) {
        public static void encode(UploadChunkPacket packet, FriendlyByteBuf buf) {
            buf.writeVarLong(packet.token());
            buf.writeVarLong(packet.offset());
            buf.writeByteArray(packet.bytes());
        }
        public static UploadChunkPacket decode(FriendlyByteBuf buf) {
            var token = buf.readVarLong();
            var offset = buf.readVarLong();
            var bytes = buf.readByteArray(CHUNK_BYTES);
            if (token < 0 || offset < 0 || bytes.length == 0 || bytes.length > CHUNK_BYTES)
                throw new IllegalArgumentException("Invalid resource upload chunk");
            return new UploadChunkPacket(token, offset, bytes);
        }
    }

    public record UploadStatusPacket(boolean success, String message) {
        public static void encode(UploadStatusPacket packet, FriendlyByteBuf buf) {
            buf.writeBoolean(packet.success());
            buf.writeUtf(packet.message(), 256);
        }
        public static UploadStatusPacket decode(FriendlyByteBuf buf) {
            return new UploadStatusPacket(buf.readBoolean(), buf.readUtf(256));
        }
    }

    public record PackEntry(String fileName, long size, String sha256) {
        private static void encode(FriendlyByteBuf buf, PackEntry entry) {
            buf.writeUtf(entry.fileName(), 128);
            buf.writeVarLong(entry.size());
            buf.writeUtf(entry.sha256(), 64);
        }

        private static PackEntry decode(FriendlyByteBuf buf) {
            return new PackEntry(buf.readUtf(128), buf.readVarLong(), buf.readUtf(64));
        }
    }

    public record ManifestPacket(long requestToken, List<PackEntry> entries) {
        public ManifestPacket { entries = List.copyOf(entries); }

        public static void encode(ManifestPacket packet, FriendlyByteBuf buf) {
            buf.writeVarLong(packet.requestToken());
            buf.writeVarInt(packet.entries().size());
            packet.entries().forEach(entry -> PackEntry.encode(buf, entry));
        }

        public static ManifestPacket decode(FriendlyByteBuf buf) {
            long requestToken = buf.readVarLong();
            if (requestToken < 0) throw new IllegalArgumentException("Invalid Photon FX manifest token");
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_PACKS) throw new IllegalArgumentException("Invalid Photon FX pack count");
            var entries = new ArrayList<PackEntry>(count);
            var hashes = new HashSet<String>();
            long total = 0;
            for (int i = 0; i < count; i++) {
                var entry = PackEntry.decode(buf);
                if (!isSafePackFileName(entry.fileName())
                        || entry.size() <= 0 || entry.size() > MAX_PACK_BYTES
                        || !entry.sha256().matches("[0-9a-f]{64}")) {
                    throw new IllegalArgumentException("Invalid Photon FX pack manifest entry");
                }
                if (!hashes.add(entry.sha256())) throw new IllegalArgumentException("Duplicate Photon FX pack hash");
                total += entry.size();
                if (total > MAX_TOTAL_BYTES) throw new IllegalArgumentException("Photon FX manifest exceeds limit");
                entries.add(entry);
            }
            return new ManifestPacket(requestToken, entries);
        }
    }

    public record RequestPacket(long requestToken, String sha256, long offset) {
        /** A request with no hash and a negative offset asks for the manifest, not for a chunk. */
        public boolean isRefresh() {
            return offset < 0 && sha256.isEmpty();
        }

        public static void encode(RequestPacket packet, FriendlyByteBuf buf) {
            buf.writeVarLong(packet.requestToken());
            buf.writeUtf(packet.sha256(), 64);
            buf.writeVarLong(packet.offset());
        }
        public static RequestPacket decode(FriendlyByteBuf buf) {
            return new RequestPacket(buf.readVarLong(), buf.readUtf(64), buf.readVarLong());
        }
    }

    public record ChunkPacket(long requestToken, String sha256, long offset, long totalSize, byte[] bytes) {
        public static void encode(ChunkPacket packet, FriendlyByteBuf buf) {
            buf.writeVarLong(packet.requestToken());
            buf.writeUtf(packet.sha256(), 64);
            buf.writeVarLong(packet.offset());
            buf.writeVarLong(packet.totalSize());
            buf.writeByteArray(packet.bytes());
        }
        public static ChunkPacket decode(FriendlyByteBuf buf) {
            var requestToken = buf.readVarLong();
            var sha = buf.readUtf(64);
            var offset = buf.readVarLong();
            var total = buf.readVarLong();
            var bytes = buf.readByteArray(CHUNK_BYTES);
            if (requestToken < 0 || !sha.matches("[0-9a-f]{64}") || offset < 0
                    || total <= 0 || total > MAX_PACK_BYTES
                    || bytes.length == 0 || bytes.length > CHUNK_BYTES || offset > total - bytes.length) {
                throw new IllegalArgumentException("Invalid Photon FX pack chunk");
            }
            return new ChunkPacket(requestToken, sha, offset, total, bytes);
        }
    }
}
