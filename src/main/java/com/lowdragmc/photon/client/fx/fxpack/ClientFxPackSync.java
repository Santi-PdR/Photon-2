package com.lowdragmc.photon.client.fx.fxpack;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonNetworking;
import com.lowdragmc.photon.network.FxPackSync;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Client cache and automatic ResourceManager reload for FX packs received from a Photon server. */
@OnlyIn(Dist.CLIENT)
public final class ClientFxPackSync {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "Photon FX pack sync");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile SyncSession activeSession;
    private static long lastRequestToken = -1;

    private ClientFxPackSync() {}

    public static void begin(long requestToken, List<FxPackSync.PackEntry> entries) {
        if (Minecraft.getInstance().getConnection() == null
                || requestToken < 0 || requestToken <= lastRequestToken) return;
        lastRequestToken = requestToken;
        var previous = activeSession;
        var session = new SyncSession(requestToken, entries.size());
        activeSession = session;
        IO.execute(() -> {
            if (previous != null) discardTemporaryFiles(previous);
            discardOrphanTemporaryFiles();
        });
        if (entries.isEmpty()) {
            finish(session);
            return;
        }
        var directory = FXPacks.getFxPacksDir().toPath();
        for (var entry : entries) {
            var target = directory.resolve("server-" + entry.sha256() + FXPacks.SUFFIX);
            IO.execute(() -> prepare(session, entry, target));
        }
    }

    private static void discardTemporaryFiles(SyncSession session) {
        for (var transfer : session.transfers.values()) {
            try {
                Files.deleteIfExists(transfer.temp);
            } catch (IOException | RuntimeException e) {
                Photon.LOGGER.warn("Could not remove temporary Photon FX pack file {}", transfer.temp, e);
            }
        }
        session.transfers.clear();
    }

    /** Remove interrupted transfers from a prior process without touching completed server cache packs. */
    private static void discardOrphanTemporaryFiles() {
        var directory = FXPacks.getFxPacksDir().toPath();
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (var path : files.filter(file -> file.getFileName().toString().startsWith("server-")
                    && file.getFileName().toString().endsWith(FXPacks.SUFFIX + ".part")
                    && Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException | RuntimeException e) {
                    Photon.LOGGER.warn("Could not remove orphaned Photon FX pack transfer {}", path, e);
                }
            }
        } catch (IOException | RuntimeException e) {
            Photon.LOGGER.warn("Could not scan for orphaned Photon FX pack transfers", e);
        }
    }

    private static void prepare(SyncSession session, FxPackSync.PackEntry entry, Path target) {
        if (activeSession != session) return;
        var temp = target.resolveSibling(target.getFileName() + ".part");
        try {
            Files.createDirectories(target.getParent());
            if (Files.isRegularFile(target) && Files.size(target) == entry.size()
                    && sha256(target).equals(entry.sha256())) {
                var validation = inspectPack(session, target);
                if (validation.valid()) {
                    if (activeSession == session) {
                        session.completed.add(entry.sha256());
                        finishOne(session);
                    }
                    return;
                }
                if (activeSession == session) {
                    throw new IOException("Cached server Photon FX pack failed validation");
                }
                return;
            }
            Files.deleteIfExists(temp);
            try (var file = new RandomAccessFile(temp.toFile(), "rw")) {
                file.setLength(entry.size());
            }
            if (activeSession != session) {
                Files.deleteIfExists(temp);
                return;
            }
            var transfer = new Transfer(entry, target, temp);
            session.transfers.put(entry.sha256(), transfer);
            if (activeSession != session) {
                session.transfers.remove(entry.sha256(), transfer);
                Files.deleteIfExists(temp);
                return;
            }
            PhotonNetworking.CHANNEL.sendToServer(
                    new FxPackSync.RequestPacket(session.requestToken, entry.sha256(), 0));
            Photon.LOGGER.info("Downloading Photon FX pack {} from server ({} bytes)", entry.fileName(), entry.size());
        } catch (IOException | RuntimeException failure) {
            session.transfers.remove(entry.sha256());
            try {
                Files.deleteIfExists(temp);
            } catch (IOException | RuntimeException cleanupFailure) {
                Photon.LOGGER.warn("Could not remove failed Photon FX pack transfer {}", temp, cleanupFailure);
            }
            if (activeSession == session) {
                Photon.LOGGER.error("Could not prepare server Photon FX pack {}", entry.fileName(), failure);
                finishOne(session);
            }
        }
    }

    public static void accept(FxPackSync.ChunkPacket packet) {
        var session = activeSession;
        if (session != null && packet.requestToken() == session.requestToken) {
            IO.execute(() -> writeChunk(session, packet));
        }
    }

    private static void writeChunk(SyncSession session, FxPackSync.ChunkPacket packet) {
        if (activeSession != session) return;
        var transfer = session.transfers.get(packet.sha256());
        if (transfer == null) return;
        synchronized (transfer) {
            try {
                if (activeSession != session) {
                    session.transfers.remove(packet.sha256(), transfer);
                    Files.deleteIfExists(transfer.temp);
                    return;
                }
                if (packet.totalSize() != transfer.entry.size() || packet.offset() != transfer.received) {
                    throw new IOException("Unexpected Photon FX pack chunk offset");
                }
                try (var file = new RandomAccessFile(transfer.temp.toFile(), "rw")) {
                    file.seek(packet.offset());
                    file.write(packet.bytes());
                }
                transfer.received += packet.bytes().length;
                if (transfer.received != transfer.entry.size()) {
                    if (activeSession == session) {
                        PhotonNetworking.CHANNEL.sendToServer(
                                new FxPackSync.RequestPacket(session.requestToken,
                                        transfer.entry.sha256(), transfer.received));
                    }
                    return;
                }
                if (!sha256(transfer.temp).equals(transfer.entry.sha256())) {
                    throw new IOException("Downloaded Photon FX pack failed integrity validation");
                }
                var validation = inspectPack(session, transfer.temp);
                if (!validation.valid()) throw new IOException("Downloaded Photon FX pack failed format validation");
                if (activeSession != session) {
                    session.transfers.remove(packet.sha256(), transfer);
                    Files.deleteIfExists(transfer.temp);
                    return;
                }
                try {
                    Files.move(transfer.temp, transfer.target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(transfer.temp, transfer.target, StandardCopyOption.REPLACE_EXISTING);
                }
                session.downloaded = true;
                session.completed.add(packet.sha256());
                session.transfers.remove(packet.sha256(), transfer);
                Photon.LOGGER.info("Downloaded Photon FX pack {}", transfer.entry.fileName());
                finishOne(session);
            } catch (IOException | RuntimeException failure) {
                session.transfers.remove(packet.sha256(), transfer);
                try {
                    Files.deleteIfExists(transfer.temp);
                } catch (IOException | RuntimeException cleanupFailure) {
                    Photon.LOGGER.warn("Could not remove failed Photon FX pack transfer {}",
                            transfer.temp, cleanupFailure);
                }
                if (activeSession == session) {
                    Photon.LOGGER.error("Photon FX pack transfer failed for {}", transfer.entry.fileName(), failure);
                    finishOne(session);
                }
            }
        }
    }

    private static FxPackSync.FxPackValidation inspectPack(SyncSession session, Path path) {
        var remaining = FxPackSync.MAX_TOTAL_EXPANDED_PACK_BYTES - session.expandedBytes;
        if (remaining <= 0) return new FxPackSync.FxPackValidation(false, 0);
        var validation = FxPackSync.inspectFxPack(path,
                Math.min(FxPackSync.MAX_EXPANDED_PACK_BYTES, remaining));
        session.expandedBytes += validation.expandedBytes();
        return validation;
    }

    private static void finishOne(SyncSession session) {
        if (activeSession != session) return;
        if (session.remaining.decrementAndGet() == 0) finish(session);
    }

    private static void finish(SyncSession session) {
        Minecraft.getInstance().execute(() -> {
            if (activeSession != session) return;
            boolean activeSetChanged = FXPacks.setActiveServerPacks(session.completed);
            if (!session.downloaded && !activeSetChanged) return;
            Photon.LOGGER.info("Photon FX server sync finished; reloading client assets automatically");
            Minecraft.getInstance().reloadResourcePacks().whenComplete((ignored, failure) -> {
                if (failure != null) Photon.LOGGER.error("Could not reload server Photon FX packs", failure);
                else Photon.LOGGER.info("Server Photon FX packs are ready");
            });
        });
    }

    /** Clear server-only assets on disconnect and invalidate any transfer still in flight. */
    public static void onClientLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        var previous = activeSession;
        activeSession = null;
        lastRequestToken = -1;
        if (previous != null) IO.execute(() -> discardTemporaryFiles(previous));
        var minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (activeSession != null || !FXPacks.setActiveServerPacks(List.of())) return;
            minecraft.reloadResourcePacks().whenComplete((ignored, failure) -> {
                if (failure != null) Photon.LOGGER.error("Could not clear disconnected server FX packs", failure);
            });
        });
    }

    private static final class SyncSession {
        private final long requestToken;
        private final Map<String, Transfer> transfers = new ConcurrentHashMap<>();
        private final java.util.Set<String> completed = ConcurrentHashMap.newKeySet();
        private final AtomicInteger remaining;
        /** Written only by the single-threaded IO executor. */
        private long expandedBytes;
        private volatile boolean downloaded;

        private SyncSession(long requestToken, int count) {
            this.requestToken = requestToken;
            remaining = new AtomicInteger(count);
        }
    }

    private static final class Transfer {
        private final FxPackSync.PackEntry entry;
        private final Path target;
        private final Path temp;
        private long received;

        private Transfer(FxPackSync.PackEntry entry, Path target, Path temp) {
            this.entry = entry;
            this.target = target;
            this.temp = temp;
        }
    }

    private static String sha256(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            final MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is required by Java", e);
            }
            var buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        }
    }

}