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
import java.util.zip.ZipFile;

/** Client cache and automatic ResourceManager reload for FX packs received from a Photon server. */
@OnlyIn(Dist.CLIENT)
public final class ClientFxPackSync {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "Photon FX pack sync");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile SyncSession activeSession;

    private ClientFxPackSync() {}

    public static void begin(List<FxPackSync.PackEntry> entries) {
        var session = new SyncSession(entries.size());
        activeSession = session;
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

    private static void prepare(SyncSession session, FxPackSync.PackEntry entry, Path target) {
        if (activeSession != session) return;
        try {
            Files.createDirectories(target.getParent());
            if (Files.isRegularFile(target) && Files.size(target) == entry.size()
                    && sha256(target).equals(entry.sha256()) && isFxPack(target)) {
                if (activeSession == session) {
                    session.completed.add(entry.sha256());
                    finishOne(session);
                }
                return;
            }
            var temp = target.resolveSibling(target.getFileName() + ".part");
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
            PhotonNetworking.CHANNEL.sendToServer(new FxPackSync.RequestPacket(entry.sha256(), 0));
            Photon.LOGGER.info("Downloading Photon FX pack {} from server ({} bytes)", entry.fileName(), entry.size());
        } catch (IOException e) {
            if (activeSession == session) {
                Photon.LOGGER.error("Could not prepare server Photon FX pack {}", entry.fileName(), e);
                finishOne(session);
            }
        }
    }

    public static void accept(FxPackSync.ChunkPacket packet) {
        var session = activeSession;
        if (session != null) IO.execute(() -> writeChunk(session, packet));
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
                                new FxPackSync.RequestPacket(transfer.entry.sha256(), transfer.received));
                    }
                    return;
                }
                if (!sha256(transfer.temp).equals(transfer.entry.sha256()) || !isFxPack(transfer.temp)) {
                    throw new IOException("Downloaded Photon FX pack failed integrity or format validation");
                }
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
            } catch (IOException e) {
                session.transfers.remove(packet.sha256(), transfer);
                try { Files.deleteIfExists(transfer.temp); } catch (IOException ignored) {}
                if (activeSession == session) {
                    Photon.LOGGER.error("Photon FX pack transfer failed for {}", transfer.entry.fileName(), e);
                    finishOne(session);
                }
            }
        }
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
        activeSession = null;
        var minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (activeSession != null || !FXPacks.setActiveServerPacks(List.of())) return;
            minecraft.reloadResourcePacks().whenComplete((ignored, failure) -> {
                if (failure != null) Photon.LOGGER.error("Could not clear disconnected server FX packs", failure);
            });
        });
    }

    private static final class SyncSession {
        private final Map<String, Transfer> transfers = new ConcurrentHashMap<>();
        private final java.util.Set<String> completed = ConcurrentHashMap.newKeySet();
        private final AtomicInteger remaining;
        private volatile boolean downloaded;

        private SyncSession(int count) {
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

    private static boolean isFxPack(Path path) {
        try (var zip = new ZipFile(path.toFile())) {
            return zip.stream().anyMatch(entry -> !entry.isDirectory()
                    && entry.getName().matches("assets/[^/]+/fx/.+\\.fx"));
        } catch (IOException e) {
            return false;
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
}
