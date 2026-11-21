package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.util.PhotonPaths;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Finds the image an effect's material names, wherever the author actually keeps it.
 * <p>
 * The normal answer is a resource id ({@code cursedfate:textures/particle/glow.png}) served by a mounted pack. But an
 * author's effect often arrives as loose files: a folder that was unzipped somewhere, a texture sitting next to the
 * {@code .fx}, an old Photon 1 tree, a pack folder that was never enabled in the resource-pack screen. Those files
 * exist on disk and the id says the namespace they belong to, but nothing in the pack stack can see them, so the draw
 * binds the missing texture and the particles come out as the purple checkerboard.
 * <p>
 * The search is a namespace-to-folder lookup over the roots the author's content actually lives in — the Photon
 * workspace ({@code <gameDir>/photon}), the old {@code ldlib}/{@code ldlib2} editor trees, and every resource-pack
 * folder the player has installed, enabled or not. Each root is read as {@code <root>/assets/<namespace>/...}, so a
 * dropped-in tree works without being registered anywhere. Matches are cached per path and cleared on a resource
 * reload, because that is when files appear and disappear.
 * <p>
 * When a loose file is found, one real {@code .png} per namespace is copied into {@code <gameDir>/photon/assets} under
 * the id it is being served as. Copying is a last resort with a purpose: the id is not just a draw-time lookup, it is
 * what {@code .fxpack} export and {@code .fx} distribution carry, so an effect whose texture only resolves through
 * this search would otherwise travel to someone else's game and lose its image. After the copy the id resolves from
 * the workspace pack like any other, and the next resource reload makes it canonical.
 */
@OnlyIn(Dist.CLIENT)
public final class LooseTextures {
    /** How many searches before the on-disk index is rebuilt: authors add files while the game runs. */
    private static final int INDEX_REBUILD_INTERVAL = 64;
    private static final int MAX_LOOSE_TEXTURE_CHOICES = 20_000;
    /** Total filesystem entries inspected across all loose asset roots in one picker catalogue build. */
    private static final int MAX_TEXTURE_SCAN_CANDIDATES = 100_000;
    /** Bound loose PNG decoding before NativeImage allocates its pixel buffer. */
    private static final long MAX_LOOSE_PNG_BYTES = 64L * 1024 * 1024;
    private static final int MAX_LOOSE_PNG_DIMENSION = 16_384;
    private static final long MAX_LOOSE_PNG_PIXELS = 16L * 1024 * 1024;
    /** Vanilla's own namespaces are never resolved from a loose folder. */
    private static final Set<String> BUILTIN = Set.of("minecraft", "realms", "brigadier");

    /** Namespaces the game owns: a picked file under {@code assets/minecraft/...} is never adopted into the workspace. */
    public static boolean isBuiltinNamespace(String namespace) {
        return BUILTIN.contains(namespace);
    }

    private static boolean safeAssetId(ResourceLocation id) {
        return PhotonPaths.isSafeAssetLocation(id);
    }

    /** A normalized workspace destination, or null for a malformed or escaping resource id. */
    @Nullable
    private static Path workspaceTarget(ResourceLocation id) {
        if (!safeAssetId(id)) return null;
        var assets = PhotonPaths.getAssetsDir().toPath().toAbsolutePath().normalize();
        var target = assets.resolve(id.getNamespace()).resolve(id.getPath()).normalize();
        return target.startsWith(assets) ? target : null;
    }

    /** Reject existing symlink components that redirect an adopted asset outside the workspace. */
    private static Path verifiedWorkspaceTarget(Path target) throws IOException {
        return canonicalContainedPath(PhotonPaths.getAssetsDir().toPath(), target);
    }

    static Path canonicalContainedPath(Path assets, Path target) throws IOException {
        var canonicalRoot = assets.toFile().getCanonicalFile().toPath();
        var canonicalTarget = target.toFile().getCanonicalFile().toPath();
        if (!canonicalTarget.startsWith(canonicalRoot)) {
            throw new IOException("Loose asset target escapes the Photon workspace: " + target);
        }
        return canonicalTarget;
    }

    private static boolean isPng(ResourceLocation id) {
        return id.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".png");
    }

    /** Check PNG dimensions from the fixed-size IHDR before NativeImage allocates pixel memory. */
    static void validateLoosePng(Path path) throws IOException {
        var size = Files.size(path);
        if (size < 24 || size > MAX_LOOSE_PNG_BYTES) {
            throw new IOException("Loose PNG size is outside the safe range: " + size);
        }
        byte[] header;
        try (var input = Files.newInputStream(path)) {
            header = input.readNBytes(24);
        }
        if (header.length != 24 || header[0] != (byte) 0x89 || header[1] != 'P'
                || header[2] != 'N' || header[3] != 'G' || header[4] != 13 || header[5] != 10
                || header[6] != 26 || header[7] != 10 || readPngInt(header, 8) != 13
                || header[12] != 'I' || header[13] != 'H' || header[14] != 'D' || header[15] != 'R') {
            throw new IOException("Loose texture does not have a valid PNG IHDR");
        }
        var width = readPngInt(header, 16);
        var height = readPngInt(header, 20);
        if (width <= 0 || height <= 0 || width > MAX_LOOSE_PNG_DIMENSION
                || height > MAX_LOOSE_PNG_DIMENSION || (long) width * height > MAX_LOOSE_PNG_PIXELS) {
            throw new IOException("Loose PNG dimensions exceed the safe limit: " + width + "x" + height);
        }
    }

    private static int readPngInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24) | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8) | (bytes[offset + 3] & 0xff);
    }

    private static boolean safePngDimensions(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_LOOSE_PNG_DIMENSION
                && height <= MAX_LOOSE_PNG_DIMENSION && (long) width * height <= MAX_LOOSE_PNG_PIXELS;
    }

    /** Replace an adopted asset only after the complete source has been copied to a safe sibling file. */
    private static void copyIntoWorkspace(Path source, Path target) throws IOException {
        target = verifiedWorkspaceTarget(target);
        Files.createDirectories(target.getParent());
        target = verifiedWorkspaceTarget(target);
        var temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.deleteIfExists(temporary);
            temporary = verifiedWorkspaceTarget(temporary);
            Files.copy(source.toRealPath(), temporary);
            verifiedWorkspaceTarget(target);
            try {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** {@code namespace:path} → the loose file serving it, or {@link #MISSING} when the search found none. */
    private static final Map<String, Object> RESOLVED = new HashMap<>();
    /** Pack lookup is hit for every material draw; resource reload is the point where this answer can change. */
    private static final Map<ResourceLocation, Boolean> PACK_SERVES = new HashMap<>();
    private static final Object MISSING = new Object();
    /** Temporary GPU textures for copied files whose namespace is not mounted yet. Render thread only. */
    private static final Map<ResourceLocation, net.minecraft.client.renderer.texture.DynamicTexture> TEMP_TEXTURES = new HashMap<>();
    private static final AtomicLong TEMP_TEXTURE_GENERATION = new AtomicLong();
    /** Render-thread-owned generation whose temporary textures have been released. */
    private static long releasedTempTextureGeneration;
    private static final Set<ResourceLocation> FAILED_UPLOADS = new LinkedHashSet<>();
    /** Namespace → first folder that holds {@code assets/<namespace>}, preserving root priority. */
    private static Map<String, Path> index = Map.of();
    /** All matching folders indexed in one pass, so picker scans don't walk every root once per namespace. */
    private static volatile Map<String, List<Path>> namespaceFolderIndex = Map.of();
    /** Negative namespace lookups avoid rescanning every asset root for each texture id in the same missing namespace. */
    private static final Set<String> missingNamespaces = new LinkedHashSet<>();
    private static int searchesSinceRebuild = 0;
    private static volatile boolean looseCatalogueMayBeIncomplete;
    private static volatile boolean assetIndexMayBeIncomplete;
    /** One warning per cache invalidation when loose-texture discovery is incomplete. */
    private static volatile boolean looseCatalogueWarningLogged;

    private LooseTextures() {
    }

    /** Drop every cached answer: a resource reload is exactly when files underneath them changed. */
    public static void invalidate() {
        RESOLVED.clear();
        PACK_SERVES.clear();
        FAILED_UPLOADS.clear();
        TEMP_TEXTURE_GENERATION.incrementAndGet();
        discardTemporaryTextures();
        index = Map.of();
        namespaceFolderIndex = Map.of();
        missingNamespaces.clear();
        searchesSinceRebuild = 0;
        looseCatalogueMayBeIncomplete = false;
        assetIndexMayBeIncomplete = false;
        looseCatalogueWarningLogged = false;
    }

    /**
     * The id a draw should bind for {@code id}: {@code id} itself when a mounted pack serves it, otherwise an id in the
     * workspace that serves the same file, otherwise {@code id} unchanged (the caller draws the missing texture, as
     * before). Never throws: a broken search must not break a frame.
     */
    public static ResourceLocation resolve(ResourceLocation id) {
        if (id == null) return null;
        if (!safeAssetId(id) || BUILTIN.contains(id.getNamespace())) return id;
        // the cache is consulted first: this runs on every draw of every texture material, and the answer only
        // changes on a resource reload, which clears it
        var key = id.toString();
        var cached = RESOLVED.get(key);
        if (cached != null) {
            return cached == MISSING ? id : (ResourceLocation) cached;
        }
        if (packServes(id)) {
            RESOLVED.put(key, id);
            return id;
        }
        try {
            var workspace = adopt(id);
            RESOLVED.put(key, workspace == null ? MISSING : workspace);
            return workspace == null ? id : workspace;
        } catch (Throwable failure) {
            Photon.LOGGER.warn("photon: could not look for a loose copy of {}", id, failure);
            RESOLVED.put(key, MISSING);
            return id;
        }
    }

    /**
     * Bindable id for the render thread. A newly copied namespace is not visible to the current pack manager until
     * reload, so register the copied image directly in TextureManager while retaining the original resource id.
     * Once a mounted pack can serve it, discard only our own temporary texture so pack priority works again.
     */
    public static ResourceLocation resolveForRender(ResourceLocation id) {
        if (id == null || !safeAssetId(id) || BUILTIN.contains(id.getNamespace())) return id;
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        discardTemporaryTexturesIfNeeded();
        var wasServed = packServes(id);
        resolve(id); // also adopts a loose file for later reloads and FX pack export
        var manager = Minecraft.getInstance().getTextureManager();
        var existing = TEMP_TEXTURES.get(id);
        if (packServes(id)) {
            // A just-adopted file can become visible immediately under an already mounted namespace, but
            // a previous lookup may have cached the missing texture under this id.
            if (!wasServed && existing == null) manager.release(id);
            if (existing != null) {
                if (manager.getTexture(id, null) == existing) manager.release(id);
                TEMP_TEXTURES.remove(id);
            }
            return id;
        }
        if (existing != null && manager.getTexture(id, null) == existing) return id;
        TEMP_TEXTURES.remove(id);
        if (FAILED_UPLOADS.contains(id)) return id;
        var target = workspaceTarget(id);
        if (target == null || !Files.isRegularFile(target)) return id;
        try {
            validateLoosePng(target);
            try (var input = Files.newInputStream(target)) {
                var image = com.mojang.blaze3d.platform.NativeImage.read(input);
                if (!safePngDimensions(image.getWidth(), image.getHeight())) {
                    image.close();
                    throw new IOException("Loose PNG dimensions exceed the safe limit");
                }
                net.minecraft.client.renderer.texture.DynamicTexture texture;
                try {
                    texture = new net.minecraft.client.renderer.texture.DynamicTexture(image);
                } catch (RuntimeException creationFailure) {
                    image.close();
                    throw creationFailure;
                }
                try {
                    manager.register(id, texture);
                    TEMP_TEXTURES.put(id, texture);
                } catch (RuntimeException registrationFailure) {
                    texture.close();
                    throw registrationFailure;
                }
            }
        } catch (Exception failure) {
            FAILED_UPLOADS.add(id);
            Photon.LOGGER.warn("photon: could not upload loose texture {} from {}", id, target, failure);
        }
        return id;
    }

    /** Whether any mounted pack already answers for this id, which needs no search at all. */
    private static boolean packServes(ResourceLocation id) {
        var cached = PACK_SERVES.get(id);
        if (cached != null) return cached;
        var manager = Minecraft.getInstance().getResourceManager();
        if (manager == null) return false;
        boolean served = manager.getResource(id).isPresent();
        PACK_SERVES.put(id, served);
        return served;
    }

    /**
     * Temporary uploads are snapshots of workspace files. Drop them on reload so changed PNGs are read again;
     * release GPU objects on the render thread and only when our texture still owns that resource id.
     */
    private static void discardTemporaryTextures() {
        if (RenderSystem.isOnRenderThread()) discardTemporaryTexturesIfNeeded();
        else RenderSystem.recordRenderCall(LooseTextures::discardTemporaryTexturesIfNeeded);
    }

    /** Called only on the render thread, and also checked synchronously before the next texture bind. */
    private static void discardTemporaryTexturesIfNeeded() {
        long generation = TEMP_TEXTURE_GENERATION.get();
        if (releasedTempTextureGeneration == generation) return;
        var manager = Minecraft.getInstance().getTextureManager();
        for (var entry : TEMP_TEXTURES.entrySet()) {
            if (manager.getTexture(entry.getKey(), null) == entry.getValue()) {
                manager.release(entry.getKey());
            }
        }
        TEMP_TEXTURES.clear();
        releasedTempTextureGeneration = generation;
    }

    private static void releaseTemporaryTexture(ResourceLocation id,
                                               net.minecraft.client.renderer.texture.DynamicTexture texture) {
        Runnable release = () -> {
            var manager = Minecraft.getInstance().getTextureManager();
            if (manager.getTexture(id, null) == texture) manager.release(id);
        };
        if (RenderSystem.isOnRenderThread()) release.run();
        else RenderSystem.recordRenderCall(() -> release.run());
    }

    /**
     * Finds {@code namespace:path} on disk and copies it into the workspace under its own id, returning the id. The
     * first {@code .png} under a namespace is taken as that namespace's texture root: the file's path inside it is the
     * id's path, which is how every pack layout is meant to work.
     */
    /**
     * Copy a loose file (texture, {@code .obj}, model json, …) into the Photon workspace under
     * {@code id} so the resource manager and FX-pack export can see it. No-op when a pack already
     * serves the id or nothing is on disk.
     */
    @Nullable
    public static ResourceLocation adoptIntoWorkspace(ResourceLocation id) {
        if (!safeAssetId(id) || BUILTIN.contains(id.getNamespace())) return null;
        if (packServes(id)) return id;
        try {
            return adopt(id);
        } catch (Exception failure) {
            Photon.LOGGER.warn("photon: could not adopt loose asset {}", id, failure);
            return null;
        }
    }

    @Nullable
    private static ResourceLocation adopt(ResourceLocation id) throws IOException {
        var target = workspaceTarget(id);
        if (target == null) return null;
        var found = find(id);
        if (found == null) return null;
        if (isPng(id)) validateLoosePng(found);
        // already there (a previous run copied it): the workspace pack serves it now
        target = verifiedWorkspaceTarget(target);
        if (Files.isRegularFile(target)) {
            if (!isPng(id)) return id;
            try {
                validateLoosePng(target);
                return id;
            } catch (IOException invalidWorkspaceCopy) {
                Photon.LOGGER.warn("photon: replacing invalid workspace PNG {} from loose source {}",
                        target, found, invalidWorkspaceCopy);
            }
        }
        copyIntoWorkspace(found, target);
        RESOLVED.remove(id.toString());
        FAILED_UPLOADS.remove(id);
        var temporaryTexture = TEMP_TEXTURES.remove(id);
        if (temporaryTexture != null) releaseTemporaryTexture(id, temporaryTexture);
        index = Map.of();
        namespaceFolderIndex = Map.of();
        missingNamespaces.clear();
        searchesSinceRebuild = 0;
        looseCatalogueMayBeIncomplete = false;
        assetIndexMayBeIncomplete = false;
        looseCatalogueWarningLogged = false;
        com.lowdragmc.photon.client.util.PhotonTextureCatalogue.invalidate();
        Photon.LOGGER.info("photon: found {} outside the packs at {} and copied it into the Photon workspace",
                id, found);
        return id;
    }

    /**
     * The file that serves {@code id} in a loose assets folder, or null.
     * <p>
     * Path-agnostic on purpose. A converted Photon 1 effect keeps the texture id it was authored with, and those ids
     * are not obliged to match the folder the artist actually delivered: {@code cursedfate:textures/particle/glow.png}
     * may live under {@code assets/othername/}, or next to the {@code .fx} with no {@code assets} folder at all. So the
     * search is: the id's own path under its namespace, then that path under <em>any</em> namespace on disk, then the
     * bare file name anywhere in the roots.
     */
    @Nullable
    public static Path find(ResourceLocation id) {
        if (!safeAssetId(id)) return null;
        var name = Path.of(id.getPath()).getFileName().toString();
        var folder = namespaceFolder(id.getNamespace());
        // A namespace can occur in several roots. The workspace may contain only some of its
        // images, so its presence must not hide an exact match in an older editor tree or pack.
        var matchingFolders = namespaceFolders(id.getNamespace());
        for (var matching : matchingFolders) {
            var direct = matching.resolve(id.getPath());
            if (Files.isRegularFile(direct)) return direct;
        }
        for (var matching : matchingFolders) {
            var loose = byFileName(matching, name, 6);
            if (loose != null) return loose;
        }
        // the same path under a namespace the author never declared as the id's
        for (var other : index.values()) {
            if (other.equals(folder)) continue;
            var direct = other.resolve(id.getPath());
            if (Files.isRegularFile(direct)) return direct;
        }
        // last resort: the file name anywhere in the roots Photon knows about, which is the "it is in my pack folder"
        // case this whole class exists for
        for (var root : roots()) {
            var loose = byFileName(root, name, 5);
            if (loose != null) return loose;
        }
        return null;
    }

    /** Every occurrence of a namespace, in root priority order, not only the first indexed one. */
    private static List<Path> namespaceFolders(String namespace) {
        var cached = namespaceFolderIndex.get(namespace);
        if (cached != null) return cached;
        if (missingNamespaces.contains(namespace)) return List.of();
        // A namespace added since the last index build still gets discovered on its first lookup.
        var found = new LinkedHashSet<Path>();
        for (var root : roots()) {
            try {
                var direct = root.resolve("assets").resolve(namespace);
                if (Files.isDirectory(direct)) found.add(direct);
                try (Stream<Path> children = Files.list(root)) {
                    children.filter(Files::isDirectory).forEach(child -> {
                        var nested = child.resolve("assets").resolve(namespace);
                        if (Files.isDirectory(nested)) found.add(nested);
                    });
                }
            } catch (IOException | java.io.UncheckedIOException | SecurityException ignored) {
                // A missing or unreadable search root contributes no folders and may truncate the picker catalogue.
                assetIndexMayBeIncomplete = true;
            }
        }
        var folders = List.copyOf(found);
        if (folders.isEmpty()) {
            missingNamespaces.add(namespace);
        } else {
            missingNamespaces.remove(namespace);
            var updated = new HashMap<>(namespaceFolderIndex);
            updated.put(namespace, folders);
            namespaceFolderIndex = java.util.Collections.unmodifiableMap(updated);
        }
        return folders;
    }

    /** The first file called {@code name} under {@code folder}, at most {@code depth} levels down. */
    @Nullable
    private static Path byFileName(Path folder, String name, int depth) {
        try (Stream<Path> files = Files.walk(folder, depth)) {
            return files.filter(path -> path.getFileName() != null
                            && path.getFileName().toString().equals(name)
                            && Files.isRegularFile(path))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }

    /** {@code assets/<namespace>} somewhere in the roots the author's content lives in. */
    @Nullable
    public static Path namespaceFolder(String namespace) {
        if (index.containsKey(namespace)) return index.get(namespace);
        if (missingNamespaces.contains(namespace)) {
            if (++searchesSinceRebuild <= INDEX_REBUILD_INTERVAL) return null;
            index = Map.of();
            namespaceFolderIndex = Map.of();
            missingNamespaces.clear();
            searchesSinceRebuild = 0;
        }
        index = buildIndex();
        var folder = index.get(namespace);
        if (folder == null) missingNamespaces.add(namespace);
        return folder;
    }

    /**
     * Every {@code assets/<namespace>} folder under the roots, the Photon workspace first.
     * <p>
     * A root is searched both as {@code <root>/assets/<ns>} and as {@code <root>/<anything>/assets/<ns>}: an author's
     * folder is not obliged to be a resource-pack root, and the common shapes on disk are "the pack folder", "the
     * folder the pack was unzipped into" and "a subfolder of a modpack's working directory". Two levels of nesting are
     * enough for all three and keep the scan shallow.
     */
    private static Map<String, Path> buildIndex() {
        var allFolders = new LinkedHashMap<String, LinkedHashSet<Path>>();
        var searchRoots = roots();
        for (var root : searchRoots) {
            indexUnder(allFolders, root.resolve("assets"));
            try (Stream<Path> children = Files.list(root)) {
                children.filter(Files::isDirectory)
                        .forEach(child -> indexUnder(allFolders, child.resolve("assets")));
            } catch (IOException | java.io.UncheckedIOException | SecurityException ignored) {
                // unreadable root: it may contain choices the picker could not index
                assetIndexMayBeIncomplete = true;
            }
        }
        var firstFolder = new LinkedHashMap<String, Path>();
        var everyFolder = new LinkedHashMap<String, List<Path>>();
        allFolders.forEach((namespace, folders) -> {
            if (folders.isEmpty()) return;
            firstFolder.put(namespace, folders.iterator().next());
            everyFolder.put(namespace, List.copyOf(folders));
        });
        namespaceFolderIndex = java.util.Collections.unmodifiableMap(everyFolder);
        missingNamespaces.removeAll(firstFolder.keySet());
        if (!firstFolder.isEmpty()) {
            Photon.LOGGER.info("photon: loose asset namespaces {} (looked in {})", firstFolder.keySet(), searchRoots);
        }
        return firstFolder;
    }

    /** Add every namespace directory, keeping the first-root search order and all fallback copies. */
    private static void indexUnder(Map<String, LinkedHashSet<Path>> found, Path assets) {
        try {
            if (!Files.isDirectory(assets)) return;
            try (Stream<Path> children = Files.list(assets)) {
                children.filter(Files::isDirectory).forEach(namespace ->
                        found.computeIfAbsent(namespace.getFileName().toString(), key -> new LinkedHashSet<>()).add(namespace));
            }
        } catch (IOException | java.io.UncheckedIOException | SecurityException ignored) {
            // An unreadable assets folder may hold namespaces that could not be offered to the picker.
            assetIndexMayBeIncomplete = true;
        }
    }

    /** The folders that may hold {@code assets/<namespace>}, most authoritative first. */
    public static List<Path> roots() {
        var roots = new ArrayList<Path>();
        // the Photon workspace, including assets/ itself so a folder dropped straight in is also seen
        roots.add(PhotonPaths.getDataDir().toPath());
        // a folder dropped straight into assets/ (rather than assets/<namespace>/) is a root of its own
        var workspaceAssets = PhotonPaths.getAssetsDir().toPath();
        try {
            if (Files.isDirectory(workspaceAssets)) roots.add(workspaceAssets);
        } catch (SecurityException denied) {
            assetIndexMayBeIncomplete = true;
        }
        // the old editor trees, which Photon still mounts for existing installations
        var game = com.lowdragmc.lowdraglib2.Platform.getGamePath();
        roots.add(game.resolve("ldlib2"));
        roots.add(game.resolve("ldlib"));
        // folders the player pointed at in the config: a modpack workspace, another drive, anywhere
        for (var extra : com.lowdragmc.photon.PhotonConfig.INSTANCE.extraAssetRoots.get()) {
            try {
                var path = Path.of(extra);
                if (Files.isDirectory(path)) roots.add(path);
            } catch (RuntimeException malformed) {
                assetIndexMayBeIncomplete = true;
                Photon.LOGGER.warn("photon: ignoring assets.extra_roots entry {}: {}", extra, malformed.getMessage());
            }
        }
        // installed resource packs, whether or not they are enabled
        var packs = game.resolve("resourcepacks");
        try {
            if (Files.isDirectory(packs)) {
                roots.add(packs);
                try (Stream<Path> children = Files.list(packs)) {
                    children.filter(Files::isDirectory).forEach(roots::add);
                }
            }
        } catch (IOException | java.io.UncheckedIOException | SecurityException ignored) {
            assetIndexMayBeIncomplete = true;
        }
        return roots.stream().distinct().toList();
    }

    /**
     * Adopt a texture selected directly from disk into Photon's workspace. A file picker may point outside every
     * configured asset root; copying it here makes the chosen resource id immediately usable and portable in FX packs.
     */
    @Nullable
    public static ResourceLocation adoptTextureFile(ResourceLocation id, Path source) {
        if (!safeAssetId(id) || source == null
                || !id.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".png")
                || !Files.isRegularFile(source)) return null;
        if (BUILTIN.contains(id.getNamespace())) return null;
        if (packServes(id)) return id;
        // The picker is a common path for imported assets. Reject corrupt/truncated files here instead of
        // adopting them and leaving a broken PNG (and a cached failed GPU upload) in the workspace.
        try {
            validateLoosePng(source);
            try (var input = Files.newInputStream(source);
                 var image = com.mojang.blaze3d.platform.NativeImage.read(input)) {
                if (!safePngDimensions(image.getWidth(), image.getHeight())) {
                    throw new IOException("Loose PNG dimensions exceed the safe limit");
                }
            }
        } catch (IOException | RuntimeException invalidImage) {
            Photon.LOGGER.warn("photon: refusing invalid PNG texture {} from {}", id, source, invalidImage);
            return null;
        }
        var target = workspaceTarget(id);
        if (target == null) return null;
        try {
            var absoluteSource = source.toRealPath();
            target = verifiedWorkspaceTarget(target);
            if (!absoluteSource.equals(target)) {
                copyIntoWorkspace(absoluteSource, target);
            }
            RESOLVED.remove(id.toString());
            FAILED_UPLOADS.remove(id);
            var texture = TEMP_TEXTURES.remove(id);
            if (texture != null) releaseTemporaryTexture(id, texture);
            index = Map.of();
            namespaceFolderIndex = Map.of();
            missingNamespaces.clear();
            searchesSinceRebuild = 0;
            looseCatalogueMayBeIncomplete = false;
            assetIndexMayBeIncomplete = false;
            looseCatalogueWarningLogged = false;
            com.lowdragmc.photon.client.util.PhotonTextureCatalogue.invalidate();
            return id;
        } catch (IOException failure) {
            Photon.LOGGER.warn("photon: could not copy selected texture {} from {}", id, source, failure);
            return null;
        }
    }

    /**
     * Texture ids found under the loose asset roots. These are not necessarily mounted resource packs, but the picker
     * can offer them because selection adopts their files into the Photon workspace before drawing. The scan is depth
     * and candidate bounded, and the catalogue is capped so a badly configured root cannot hang the editor.
     */
    public static List<ResourceLocation> listLooseTextureIds() {
        assetIndexMayBeIncomplete = false;
        index = buildIndex();
        searchesSinceRebuild = 0;
        var folders = new LinkedHashMap<String, List<Path>>();
        for (var namespace : index.keySet()) folders.put(namespace, namespaceFolders(namespace));
        var scan = scanTextureFolders(folders, MAX_LOOSE_TEXTURE_CHOICES, MAX_TEXTURE_SCAN_CANDIDATES);
        boolean incomplete = scan.truncated() || assetIndexMayBeIncomplete;
        looseCatalogueMayBeIncomplete = incomplete;
        if (incomplete && !looseCatalogueWarningLogged) {
            looseCatalogueWarningLogged = true;
            Photon.LOGGER.warn("Photon's loose texture picker may be incomplete ({} ids, {} filesystem paths total, unreadable folder: {}); some choices may be omitted",
                    MAX_LOOSE_TEXTURE_CHOICES, MAX_TEXTURE_SCAN_CANDIDATES,
                    scan.unreadable() || assetIndexMayBeIncomplete);
        }
        return scan.locations();
    }

    /** Whether the last loose-texture scan may have omitted choices due to a cap or filesystem error. */
    public static boolean looseTextureCatalogueMayBeIncomplete() {
        return looseCatalogueMayBeIncomplete;
    }

    /** The filesystem-only scan, factored so the global candidate and result caps can be regression-tested. */
    record LooseTextureScan(List<ResourceLocation> locations, boolean truncated, int scannedCandidates, boolean unreadable) {}

    static LooseTextureScan scanTextureFolders(Map<String, List<Path>> foldersByNamespace,
                                               int maxChoices, int maxCandidates) {
        var found = new LinkedHashSet<ResourceLocation>();
        int scannedCandidates = 0;
        boolean truncated = false;
        boolean unreadable = false;
        outer:
        for (var entry : foldersByNamespace.entrySet()) {
            var namespace = entry.getKey();
            if (BUILTIN.contains(namespace)) continue;
            for (var folder : entry.getValue()) {
                var textures = folder.resolve("textures");
                try {
                    if (!Files.isDirectory(textures)) continue;
                } catch (SecurityException denied) {
                    truncated = true;
                    unreadable = true;
                    continue;
                }
                try (Stream<Path> files = Files.walk(textures, 12)) {
                    var paths = files.iterator();
                    while (paths.hasNext() && scannedCandidates < maxCandidates && found.size() < maxChoices) {
                        var path = paths.next();
                        scannedCandidates++;
                        if (!Files.isRegularFile(path) || path.getFileName() == null
                                || !path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                            continue;
                        }
                        var relative = textures.getParent().relativize(path).toString().replace('\\', '/');
                        var location = ResourceLocation.tryParse(namespace + ":" + relative);
                        if (location != null) found.add(location);
                    }
                } catch (IOException | java.io.UncheckedIOException | SecurityException ignored) {
                    // An unreadable asset folder contributes no choices, so the list may be incomplete.
                    truncated = true;
                    unreadable = true;
                }
                if (found.size() >= maxChoices || scannedCandidates >= maxCandidates) {
                    truncated = true;
                    break outer;
                }
            }
        }
        return new LooseTextureScan(found.stream()
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString)).toList(),
                truncated, scannedCandidates, unreadable);
    }

    /** Whether a loose folder happens to carry this id, for the diagnostics command. */
    public static boolean hasLooseCopy(ResourceLocation id) {
        return find(id) != null;
    }

    /**
     * The {@code /photon_client textures} answer: which folders are searched, which namespaces each one offers, and
     * every texture id the loaded effects name with whether a pack or a loose folder serves it. This exists because
     * "the particles do not show" has three different causes — no id at all, an id nothing serves, an id served by the
     * wrong file — and the log alone never says which one it was.
     */
    public static void reportTexturesTo(java.util.function.Consumer<String> out) {
        out.accept("photon texture lookup");
        out.accept("  searched roots:");
        for (var root : roots()) {
            var assets = root.resolve("assets");
            out.accept("    " + root + (java.nio.file.Files.isDirectory(assets) ? "" : "   (no assets folder)"));
        }
        var namespaces = buildIndex();
        out.accept("  namespaces found on disk: " + (namespaces.isEmpty() ? "none" : namespaces.keySet()));
        if (com.lowdragmc.photon.client.util.PhotonTextureCatalogue.isCatalogueIncomplete()) {
            out.accept("  texture picker catalogue may be incomplete; some choices may be omitted");
        }
        for (var entry : namespaces.entrySet()) {
            out.accept("    " + entry.getKey() + " -> " + entry.getValue());
        }
        var ids = new java.util.LinkedHashSet<ResourceLocation>();
        var loadedEffects = 0;
        for (var fxId : FXHelper.listAllFX()) {
            // only effects already in memory: a diagnosis must not load a hundred files or start a load chain
            var fx = FXHelper.getCachedFX(fxId);
            if (fx == null) continue;
            loadedEffects++;
            ids.addAll(FXPreloader.texturesOf(fx));
        }
        if (ids.isEmpty()) {
            out.accept("  " + loadedEffects + " of " + FXHelper.listAllFX().size()
                    + " effect(s) loaded so far; none names a texture yet (open or play one first)");
            return;
        }
        out.accept("  textures named by loaded effects:");
        for (var id : ids) {
            var status = packServes(id) ? "pack" : hasLooseCopy(id) ? "loose folder (copied in on use)" : "MISSING";
            out.accept("    " + id + "  <- " + status);
        }
    }
}
