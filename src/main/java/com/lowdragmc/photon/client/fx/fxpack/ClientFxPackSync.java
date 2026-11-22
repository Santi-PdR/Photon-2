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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client cache and automatic ResourceManager reload for FX packs received from a Photon server.
 * <p>
 * Chunk transfers are driven by request/response: if the server stops sending (half-open connection,
 * crash after the manifest), a transfer would wait forever and the session would never activate its
 * packs. A watchdog sweeps transfers that see no chunk activity — and no IO work to explain it — for
 * {@link #STALL_TIMEOUT_NANOS}, failing them through the same path as a corrupt chunk so the session
 * still finishes with whatever downloaded.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientFxPackSync {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "Photon FX pack sync");
        thread.setDaemon(true);
        return thread;
    });
    /** Touched at every IO task start and end: while the IO thread works, a quiet transfer is not a stall. */
    private static final AtomicLong IO_ACTIVITY = new AtomicLong();
    /** No chunk arrivals and no IO work for this long means the server stopped answering. */
    static final long STALL_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(120);
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "Photon FX pack sync watchdog");
        thread.setDaemon(true);
        return thread;
    });

    static {
        WATCHDOG.scheduleWithFixedDelay(ClientFxPackSync::checkStalls, 30, 30, TimeUnit.SECONDS);
    }

    /** Package-visible so the stall sweep can be regression-tested against a synthetic session. */
    static volatile SyncSession activeSession;
    private static long lastRequestToken = -1;
    /** Sessions that reached {@link #finish} (game thread); the smoke test waits on it after asking for a re-offer. */
    private static volatile int finishedSessions;

    private ClientFxPackSync() {}

    private static void submitIO(Runnable task) {
        IO.execute(() -> {
            IO_ACTIVITY.set(System.nanoTime());
            try {
                task.run();
            } finally {
                IO_ACTIVITY.set(System.nanoTime());
            }
        });
    }

    private static void checkStalls() {
        if (sweepSuspended) return;
        var session = activeSession;
        if (session == null) return;
        sweepStalled(session, System.nanoTime(), IO_ACTIVITY.get());
    }

    /** Set while a regression drives {@link #sweepStalled} with fabricated times, so the live
     *  watchdog cannot sweep its synthetic transfers out from under its assertions. */
    static volatile boolean sweepSuspended;

    /** Fail every transfer of {@code session} that has seen no activity for the timeout while the IO
     *  thread was idle. Package-private so the stall sweep is testable without a server. */
    static void sweepStalled(SyncSession session, long nowNanos, long ioActivityNanos) {
        if (nowNanos - ioActivityNanos < STALL_TIMEOUT_NANOS) return;
        for (var transfer : List.copyOf(session.transfers.values())) {
            if (nowNanos - transfer.lastActivity < STALL_TIMEOUT_NANOS) continue;
            failStalled(session, transfer);
        }
    }

    /** The writeChunk failure path for a transfer the server stopped feeding. */
    private static void failStalled(SyncSession session, Transfer transfer) {
        synchronized (transfer) {
            if (!session.transfers.remove(transfer.entry.sha256(), transfer)) return;
            try {
                Files.deleteIfExists(transfer.temp);
            } catch (IOException | RuntimeException cleanupFailure) {
                Photon.LOGGER.warn("Could not remove stalled Photon FX pack transfer {}", transfer.temp, cleanupFailure);
            }
            if (activeSession == session) {
                Photon.LOGGER.warn("Photon FX pack transfer {} stalled with no server chunks; dropping it",
                        transfer.entry.fileName());
                finishOne(session);
            }
        }
    }

    public static void begin(long requestToken, List<FxPackSync.PackEntry> entries) {
        if (Minecraft.getInstance().getConnection() == null
                || requestToken < 0 || requestToken <= lastRequestToken) return;
        lastRequestToken = requestToken;
        var previous = activeSession;
        var session = new SyncSession(requestToken, entries.size());
        activeSession = session;
        submitIO(() -> {
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
            submitIO(() -> prepare(session, entry, target));
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
            var installed = installedCopy(target.getParent(), entry);
            if (installed != null) {
                // Every .fxpack in photon/fxpacks is mounted by this client on its own, so the offer is already met:
                // nothing to download and nothing to mount a second time. The usual case is singleplayer, where the
                // integrated server scans this very folder and offered the player their own exports, which came
                // back as server-<hash>.fxpack copies of them ("why are there two random files").
                Photon.LOGGER.info("Photon FX pack {} offered by the server is already installed as {}; using that copy",
                        entry.fileName(), installed.getFileName());
                dropDuplicateCache(target, installed);
                if (activeSession == session) finishOne(session);
                return;
            }
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
            // arrival is the progress signal the watchdog watches, even while the IO thread is busy
            var transfer = session.transfers.get(packet.sha256());
            if (transfer != null) transfer.touch();
            submitIO(() -> writeChunk(session, packet));
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

    /**
     * A pack the player installed themselves — a {@code .fxpack} directly in {@code directory} whose name is not ours
     * ({@code server-*}) — with exactly the offered content, or null. Only files of the offered size are hashed.
     */
    @javax.annotation.Nullable
    static Path installedCopy(Path directory, FxPackSync.PackEntry entry) {
        if (!Files.isDirectory(directory)) return null;
        try (var files = Files.list(directory)) {
            for (var path : files.sorted().toList()) {
                var name = path.getFileName().toString();
                if (name.startsWith("server-") || !name.endsWith(FXPacks.SUFFIX)
                        || !Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    if (Files.size(path) == entry.size() && sha256(path).equals(entry.sha256())) return path;
                } catch (IOException | RuntimeException unreadable) {
                    // a pack being written or unreadable is simply not a match
                }
            }
        } catch (IOException | RuntimeException e) {
            Photon.LOGGER.warn("Could not look for an installed copy of Photon FX pack {}", entry.fileName(), e);
        }
        return null;
    }

    /**
     * A {@code server-<hash>} copy made before installed packs were recognised duplicates one the player has; it is
     * our own cache file, so drop it. Best effort: while it is mounted, Windows refuses the delete — the next offer,
     * once it is no longer active, gets it.
     */
    private static void dropDuplicateCache(Path target, Path installed) {
        try {
            if (Files.deleteIfExists(target)) {
                Photon.LOGGER.info("Removed {}, a cached copy of the installed Photon FX pack {}",
                        target.getFileName(), installed.getFileName());
            }
        } catch (IOException | RuntimeException busy) {
            Photon.LOGGER.debug("Could not remove cached Photon FX pack copy {} yet", target, busy);
        }
    }

    /** How many sync sessions have finished since the client started. */
    public static int finishedSessions() {
        return finishedSessions;
    }

    private static void finishOne(SyncSession session) {
        if (activeSession != session) return;
        if (session.remaining.decrementAndGet() == 0) finish(session);
    }

    private static void finish(SyncSession session) {
        Minecraft.getInstance().execute(() -> {
            if (activeSession != session) return;
            finishedSessions++;
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
        LocalFxResourceUpload.onClientLogout();
        var previous = activeSession;
        activeSession = null;
        lastRequestToken = -1;
        if (previous != null) submitIO(() -> discardTemporaryFiles(previous));
        var minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (activeSession != null || !FXPacks.setActiveServerPacks(List.of())) return;
            minecraft.reloadResourcePacks().whenComplete((ignored, failure) -> {
                if (failure != null) Photon.LOGGER.error("Could not clear disconnected server FX packs", failure);
            });
        });
    }

    static final class SyncSession {
        private final long requestToken;
        final Map<String, Transfer> transfers = new ConcurrentHashMap<>();
        private final java.util.Set<String> completed = ConcurrentHashMap.newKeySet();
        private final AtomicInteger remaining;
        /** Written only by the single-threaded IO executor. */
        private long expandedBytes;
        private volatile boolean downloaded;

        SyncSession(long requestToken, int count) {
            this.requestToken = requestToken;
            remaining = new AtomicInteger(count);
        }
    }

    static final class Transfer {
        final FxPackSync.PackEntry entry;
        private final Path target;
        final Path temp;
        private long received;
        /** Last chunk arrival or IO touch; the watchdog sweeps transfers this goes stale on. */
        volatile long lastActivity = System.nanoTime();

        Transfer(FxPackSync.PackEntry entry, Path target, Path temp) {
            this.entry = entry;
            this.target = target;
            this.temp = temp;
        }

        void touch() {
            lastActivity = System.nanoTime();
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