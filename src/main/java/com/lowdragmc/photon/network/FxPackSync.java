package com.lowdragmc.photon.network;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

/** Server-to-client distribution of author-provided Photon FX packs. */
public final class FxPackSync {
    public static final String PACK_DIRECTORY = "photon/fxpacks";
    public static final String SUFFIX = ".fxpack";
    public static final int CHUNK_BYTES = 24 * 1024;
    public static final long MAX_PACK_BYTES = 64L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 128L * 1024 * 1024;
    private static final long MAX_EXPANDED_PACK_BYTES = 256L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 20_000;
    private static final int MAX_PACKS = 32;
    private static final Map<UUID, Map<String, PackEntry>> OFFERED = new ConcurrentHashMap<>();

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

    private static void offer(ServerPlayer player, boolean requested) {
        var server = player.getServer();
        if (server == null) return;
        // every offer re-scans the folder: a pack added a minute ago is on disk now, and the hash is what decides
        var entries = scan(server);
        var offered = new ConcurrentHashMap<String, PackEntry>();
        entries.forEach(entry -> offered.put(entry.sha256(), entry));
        OFFERED.put(player.getUUID(), Map.copyOf(offered));
        PhotonNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ManifestPacket(entries));
        Photon.LOGGER.info("{} {} Photon FX pack(s) to {}", requested ? "Re-offered" : "Offered", entries.size(),
                player.getGameProfile().getName());
    }

    public static void onPlayerLogout(ServerPlayer player) {
        OFFERED.remove(player.getUUID());
    }

    private static List<PackEntry> scan(MinecraftServer server) {
        var directory = server.getServerDirectory().toPath().resolve(PACK_DIRECTORY);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            Photon.LOGGER.error("Could not create Photon FX pack directory {}", directory, e);
            return List.of();
        }
        var result = new ArrayList<PackEntry>();
        var seenHashes = new HashSet<String>();
        long totalBytes = 0;
        try (var files = Files.list(directory)) {
            for (var path : files.filter(file -> Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    .filter(file -> file.getFileName().toString().endsWith(SUFFIX)
                            && file.getFileName().toString().length() <= 128).sorted().toList()) {
                if (result.size() >= MAX_PACKS) break;
                var size = Files.size(path);
                if (size <= 0 || size > MAX_PACK_BYTES || totalBytes + size > MAX_TOTAL_BYTES) {
                    Photon.LOGGER.warn("Photon FX sync skipped {} (size {} exceeds limits)", path.getFileName(), size);
                    continue;
                }
                if (!isFxPack(path)) {
                    Photon.LOGGER.warn("Photon FX sync skipped invalid pack {}", path.getFileName());
                    continue;
                }
                var hash = sha256(path);
                if (!seenHashes.add(hash)) continue;
                result.add(new PackEntry(path.getFileName().toString(), size, hash));
                totalBytes += size;
            }
        } catch (IOException e) {
            Photon.LOGGER.error("Could not scan server Photon FX packs in {}", directory, e);
        }
        return List.copyOf(result);
    }

    private static boolean isFxPack(Path path) {
        try (var zip = new ZipFile(path.toFile())) {
            var entries = zip.entries();
            int count = 0;
            long expanded = 0;
            boolean hasFx = false;
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var name = entry.getName();
                if (++count > MAX_ZIP_ENTRIES || name.startsWith("/") || name.contains("\\")
                        || name.contains("../") || name.equals("..")) return false;
                if (entry.getSize() > 0) {
                    expanded += entry.getSize();
                    if (expanded > MAX_EXPANDED_PACK_BYTES) return false;
                }
                if (!entry.isDirectory() && name.matches("assets/[^/]+/fx/.+\\.fx")) hasFx = true;
            }
            return hasFx;
        } catch (IOException e) {
            return false;
        }
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
                com.lowdragmc.photon.client.fx.fxpack.ClientFxPackSync.begin(packet.entries()));
    }

    public static void handleRequest(RequestPacket packet, ServerPlayer player) {
        if (player == null) return;
        if (packet.isRefresh()) {
            Photon.LOGGER.info("Photon FX pack refresh requested by {}", player.getGameProfile().getName());
            onRefreshRequest(player);
            return;
        }
        if (packet.offset() < 0 || !packet.sha256().matches("[0-9a-f]{64}")) return;
        var entry = OFFERED.getOrDefault(player.getUUID(), Map.of()).get(packet.sha256());
        if (entry == null || packet.offset() >= entry.size()) return;
        var server = player.getServer();
        if (server == null) return;
        var directory = server.getServerDirectory().toPath().resolve(PACK_DIRECTORY).normalize();
        var path = directory.resolve(entry.fileName()).normalize();
        if (!path.getParent().equals(directory)) return;
        try (var file = new RandomAccessFile(path.toFile(), "r")) {
            if (file.length() != entry.size()) return;
            file.seek(packet.offset());
            var bytes = new byte[(int) Math.min(CHUNK_BYTES, entry.size() - packet.offset())];
            file.readFully(bytes);
            PhotonNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new ChunkPacket(entry.sha256(), packet.offset(), entry.size(), bytes));
        } catch (IOException e) {
            Photon.LOGGER.warn("Could not stream Photon FX pack {} to {}", entry.fileName(),
                    player.getGameProfile().getName(), e);
        }
    }

    /** Asks the server for the current pack list, for the client command, without the offset/chunk machinery. */
    public static RequestPacket refreshRequest() {
        return new RequestPacket("", -1);
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

    public record ManifestPacket(List<PackEntry> entries) {
        public ManifestPacket { entries = List.copyOf(entries); }

        public static void encode(ManifestPacket packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.entries().size());
            packet.entries().forEach(entry -> PackEntry.encode(buf, entry));
        }

        public static ManifestPacket decode(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_PACKS) throw new IllegalArgumentException("Invalid Photon FX pack count");
            var entries = new ArrayList<PackEntry>(count);
            var hashes = new HashSet<String>();
            long total = 0;
            for (int i = 0; i < count; i++) {
                var entry = PackEntry.decode(buf);
                if (entry.fileName().isBlank() || entry.fileName().contains("/") || entry.fileName().contains("\\")
                        || !entry.fileName().endsWith(SUFFIX) || entry.size() <= 0 || entry.size() > MAX_PACK_BYTES
                        || !entry.sha256().matches("[0-9a-f]{64}")) {
                    throw new IllegalArgumentException("Invalid Photon FX pack manifest entry");
                }
                if (!hashes.add(entry.sha256())) throw new IllegalArgumentException("Duplicate Photon FX pack hash");
                total += entry.size();
                if (total > MAX_TOTAL_BYTES) throw new IllegalArgumentException("Photon FX manifest exceeds limit");
                entries.add(entry);
            }
            return new ManifestPacket(entries);
        }
    }

    public record RequestPacket(String sha256, long offset) {
        /** A request with no hash and a negative offset asks for the manifest, not for a chunk. */
        public boolean isRefresh() {
            return offset < 0 && sha256.isEmpty();
        }

        public static void encode(RequestPacket packet, FriendlyByteBuf buf) {
            buf.writeUtf(packet.sha256(), 64);
            buf.writeVarLong(packet.offset());
        }
        public static RequestPacket decode(FriendlyByteBuf buf) {
            return new RequestPacket(buf.readUtf(64), buf.readVarLong());
        }
    }

    public record ChunkPacket(String sha256, long offset, long totalSize, byte[] bytes) {
        public static void encode(ChunkPacket packet, FriendlyByteBuf buf) {
            buf.writeUtf(packet.sha256(), 64);
            buf.writeVarLong(packet.offset());
            buf.writeVarLong(packet.totalSize());
            buf.writeByteArray(packet.bytes());
        }
        public static ChunkPacket decode(FriendlyByteBuf buf) {
            var sha = buf.readUtf(64);
            var offset = buf.readVarLong();
            var total = buf.readVarLong();
            var bytes = buf.readByteArray(CHUNK_BYTES);
            if (!sha.matches("[0-9a-f]{64}") || offset < 0 || total <= 0 || total > MAX_PACK_BYTES
                    || bytes.length == 0 || bytes.length > CHUNK_BYTES || offset > total - bytes.length) {
                throw new IllegalArgumentException("Invalid Photon FX pack chunk");
            }
            return new ChunkPacket(sha, offset, total, bytes);
        }
    }
}
