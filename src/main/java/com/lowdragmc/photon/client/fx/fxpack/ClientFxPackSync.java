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
import java.util.Set;
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
    private static final Map<String, Transfer> TRANSFERS = new ConcurrentHashMap<>();
    private static final Set<String> COMPLETED = ConcurrentHashMap.newKeySet();
    private static AtomicInteger remaining = new AtomicInteger();
    private static volatile boolean downloaded;

    private ClientFxPackSync() {}

    public static void begin(List<FxPackSync.PackEntry> entries) {
        TRANSFERS.clear();
        COMPLETED.clear();
        downloaded = false;
        remaining = new AtomicInteger(entries.size());
        if (entries.isEmpty()) {
            finish();
            return;
        }
        var directory = FXPacks.getFxPacksDir().toPath();
        for (var entry : entries) {
            var target = directory.resolve("server-" + entry.sha256() + FXPacks.SUFFIX);
            IO.execute(() -> prepare(entry, target));
        }
    }

    private static void prepare(FxPackSync.PackEntry entry, Path target) {
        try {
            Files.createDirectories(target.getParent());
            if (Files.isRegularFile(target) && Files.size(target) == entry.size()
                    && sha256(target).equals(entry.sha256()) && isFxPack(target)) {
                COMPLETED.add(entry.sha256());
                finishOne();
                return;
            }
            var temp = target.resolveSibling(target.getFileName() + ".part");
            Files.deleteIfExists(temp);
            try (var file = new RandomAccessFile(temp.toFile(), "rw")) {
                file.setLength(entry.size());
            }
            TRANSFERS.put(entry.sha256(), new Transfer(entry, target, temp));
            PhotonNetworking.CHANNEL.sendToServer(new FxPackSync.RequestPacket(entry.sha256(), 0));
            Photon.LOGGER.info("Downloading Photon FX pack {} from server ({} bytes)", entry.fileName(), entry.size());
        } catch (IOException e) {
            Photon.LOGGER.error("Could not prepare server Photon FX pack {}", entry.fileName(), e);
            finishOne();
        }
    }

    public static void accept(FxPackSync.ChunkPacket packet) {
        IO.execute(() -> writeChunk(packet));
    }

    private static void writeChunk(FxPackSync.ChunkPacket packet) {
        var transfer = TRANSFERS.get(packet.sha256());
        if (transfer == null) return;
        synchronized (transfer) {
            try {
                if (packet.totalSize() != transfer.entry.size() || packet.offset() != transfer.received) {
                    throw new IOException("Unexpected Photon FX pack chunk offset");
                }
                try (var file = new RandomAccessFile(transfer.temp.toFile(), "rw")) {
                    file.seek(packet.offset());
                    file.write(packet.bytes());
                }
                transfer.received += packet.bytes().length;
                if (transfer.received != transfer.entry.size()) {
                    PhotonNetworking.CHANNEL.sendToServer(
                            new FxPackSync.RequestPacket(transfer.entry.sha256(), transfer.received));
                    return;
                }
                if (!sha256(transfer.temp).equals(transfer.entry.sha256()) || !isFxPack(transfer.temp)) {
                    throw new IOException("Downloaded Photon FX pack failed integrity or format validation");
                }
                try {
                    Files.move(transfer.temp, transfer.target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(transfer.temp, transfer.target, StandardCopyOption.REPLACE_EXISTING);
                }
                downloaded = true;
                COMPLETED.add(packet.sha256());
                TRANSFERS.remove(packet.sha256());
                Photon.LOGGER.info("Downloaded Photon FX pack {}", transfer.entry.fileName());
                finishOne();
            } catch (IOException e) {
                TRANSFERS.remove(packet.sha256());
                try { Files.deleteIfExists(transfer.temp); } catch (IOException ignored) {}
                Photon.LOGGER.error("Photon FX pack transfer failed for {}", transfer.entry.fileName(), e);
                finishOne();
            }
        }
    }

    private static void finishOne() {
        if (remaining.decrementAndGet() == 0) finish();
    }

    private static void finish() {
        Minecraft.getInstance().execute(() -> {
            boolean activeSetChanged = FXPacks.setActiveServerPacks(COMPLETED);
            if (!downloaded && !activeSetChanged) return;
            Photon.LOGGER.info("Photon FX server sync finished; reloading client assets automatically");
            Minecraft.getInstance().reloadResourcePacks().whenComplete((ignored, failure) -> {
                if (failure != null) Photon.LOGGER.error("Could not reload server Photon FX packs", failure);
                else Photon.LOGGER.info("Server Photon FX packs are ready");
            });
        });
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
