package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.util.PhotonPaths;
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
    /** Vanilla's own namespaces are never resolved from a loose folder. */
    private static final Set<String> BUILTIN = Set.of("minecraft", "realms", "brigadier");

    /** {@code namespace:path} → the loose file serving it, or {@link #MISSING} when the search found none. */
    private static final Map<String, Object> RESOLVED = new HashMap<>();
    private static final Object MISSING = new Object();
    /** Namespaces already copied into the workspace, so a miss is not retried every draw. */
    private static final Set<String> COPIED = new LinkedHashSet<>();
    /** Temporary GPU textures for copied files whose namespace is not mounted yet. Render thread only. */
    private static final Map<ResourceLocation, net.minecraft.client.renderer.texture.DynamicTexture> TEMP_TEXTURES = new HashMap<>();
    private static final Set<ResourceLocation> FAILED_UPLOADS = new LinkedHashSet<>();
    /** Namespace → folder that holds {@code assets/<namespace>}. */
    private static Map<String, Path> index = Map.of();
    private static int searchesSinceRebuild = 0;

    private LooseTextures() {
    }

    /** Drop every cached answer: a resource reload is exactly when files underneath them changed. */
    public static void invalidate() {
        RESOLVED.clear();
        COPIED.clear();
        FAILED_UPLOADS.clear();
        index = Map.of();
        searchesSinceRebuild = 0;
    }

    /**
     * The id a draw should bind for {@code id}: {@code id} itself when a mounted pack serves it, otherwise an id in the
     * workspace that serves the same file, otherwise {@code id} unchanged (the caller draws the missing texture, as
     * before). Never throws: a broken search must not break a frame.
     */
    public static ResourceLocation resolve(ResourceLocation id) {
        if (id == null) return null;
        if (BUILTIN.contains(id.getNamespace())) return id;
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
        if (id == null || BUILTIN.contains(id.getNamespace())) return id;
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
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
        var target = PhotonPaths.getAssetsDir().toPath().resolve(id.getNamespace()).resolve(id.getPath());
        if (!Files.isRegularFile(target)) return id;
        try (var input = Files.newInputStream(target)) {
            var image = com.mojang.blaze3d.platform.NativeImage.read(input);
            var texture = new net.minecraft.client.renderer.texture.DynamicTexture(image);
            manager.register(id, texture);
            TEMP_TEXTURES.put(id, texture);
        } catch (Exception failure) {
            FAILED_UPLOADS.add(id);
            Photon.LOGGER.warn("photon: could not upload loose texture {} from {}", id, target, failure);
        }
        return id;
    }

    /** Whether any mounted pack already answers for this id, which needs no search at all. */
    private static boolean packServes(ResourceLocation id) {
        var manager = Minecraft.getInstance().getResourceManager();
        return manager != null && manager.getResource(id).isPresent();
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
        if (id == null || BUILTIN.contains(id.getNamespace())) return null;
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
        var found = find(id);
        if (found == null) return null;
        var target = PhotonPaths.getAssetsDir().toPath()
                .resolve(id.getNamespace()).resolve(id.getPath());
        // already there (a previous run copied it): the workspace pack serves it now
        if (Files.isRegularFile(target)) return id;
        Files.createDirectories(target.getParent());
        Files.copy(found, target);
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
        for (var other : buildIndex().values()) {
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
        var found = new LinkedHashSet<Path>();
        for (var root : roots()) {
            var direct = root.resolve("assets").resolve(namespace);
            if (Files.isDirectory(direct)) found.add(direct);
            try (Stream<Path> children = Files.list(root)) {
                children.filter(Files::isDirectory).forEach(child -> {
                    var nested = child.resolve("assets").resolve(namespace);
                    if (Files.isDirectory(nested)) found.add(nested);
                });
            } catch (IOException ignored) {
                // A missing or unreadable search root contributes no folders.
            }
        }
        return List.copyOf(found);
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
        if (++searchesSinceRebuild > INDEX_REBUILD_INTERVAL) {
            index = Map.of();
            searchesSinceRebuild = 0;
        }
        index = buildIndex();
        return index.get(namespace);
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
        Map<String, Path> found = new LinkedHashMap<>();
        for (var root : roots()) {
            indexUnder(found, root.resolve("assets"));
            try (Stream<Path> children = Files.list(root)) {
                children.filter(Files::isDirectory)
                        .forEach(child -> indexUnder(found, child.resolve("assets")));
            } catch (IOException ignored) {
                // unreadable root: nothing to index there
            }
        }
        if (!found.isEmpty()) {
            Photon.LOGGER.info("photon: loose asset namespaces {} (looked in {})", found.keySet(), roots());
        }
        return found;
    }

    /** Adds the namespace folders of one {@code assets} directory; the first root to offer a name keeps it. */
    private static void indexUnder(Map<String, Path> found, Path assets) {
        if (!Files.isDirectory(assets)) return;
        try (Stream<Path> children = Files.list(assets)) {
            children.filter(Files::isDirectory).forEach(namespace -> {
                var name = namespace.getFileName().toString();
                // first root wins: the workspace outranks the old editor trees and installed packs
                found.putIfAbsent(name, namespace);
            });
        } catch (IOException ignored) {
            // unreadable assets folder
        }
    }

    /** The folders that may hold {@code assets/<namespace>}, most authoritative first. */
    public static List<Path> roots() {
        var roots = new ArrayList<Path>();
        // the Photon workspace, including assets/ itself so a folder dropped straight in is also seen
        roots.add(PhotonPaths.getDataDir().toPath());
        // a folder dropped straight into assets/ (rather than assets/<namespace>/) is a root of its own
        var workspaceAssets = PhotonPaths.getAssetsDir().toPath();
        if (Files.isDirectory(workspaceAssets)) roots.add(workspaceAssets);
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
                Photon.LOGGER.warn("photon: ignoring assets.extra_roots entry {}: {}", extra, malformed.getMessage());
            }
        }
        // installed resource packs, whether or not they are enabled
        var packs = game.resolve("resourcepacks");
        if (Files.isDirectory(packs)) {
            roots.add(packs);
            try (Stream<Path> children = Files.list(packs)) {
                children.filter(Files::isDirectory).forEach(roots::add);
            } catch (IOException ignored) {
                // no pack folders to descend into
            }
        }
        return roots.stream().distinct().toList();
    }

    /**
     * Adopt a texture selected directly from disk into Photon's workspace. A file picker may point outside every
     * configured asset root; copying it here makes the chosen resource id immediately usable and portable in FX packs.
     */
    @Nullable
    public static ResourceLocation adoptTextureFile(ResourceLocation id, Path source) {
        if (id == null || source == null
                || !id.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".png")
                || !Files.isRegularFile(source)) return null;
        if (packServes(id)) return id;
        if (BUILTIN.contains(id.getNamespace())) return null;
        var target = PhotonPaths.getAssetsDir().toPath().resolve(id.getNamespace()).resolve(id.getPath()).normalize();
        var assets = PhotonPaths.getAssetsDir().toPath().toAbsolutePath().normalize();
        if (!target.toAbsolutePath().normalize().startsWith(assets)) return null;
        try {
            var absoluteSource = source.toAbsolutePath().normalize();
            if (!absoluteSource.equals(target.toAbsolutePath().normalize())) {
                Files.createDirectories(target.getParent());
                Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            RESOLVED.remove(id.toString());
            COPIED.remove(id.getNamespace());
            FAILED_UPLOADS.remove(id);
            var texture = TEMP_TEXTURES.remove(id);
            if (texture != null && com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
                var manager = Minecraft.getInstance().getTextureManager();
                if (manager.getTexture(id, null) == texture) manager.release(id);
            }
            index = Map.of();
            searchesSinceRebuild = 0;
            com.lowdragmc.photon.client.util.PhotonTextureCatalogue.invalidate();
            return id;
        } catch (IOException failure) {
            Photon.LOGGER.warn("photon: could not copy selected texture {} from {}", id, source, failure);
            return null;
        }
    }

    /**
     * Texture ids found under the loose asset roots. These are not necessarily mounted resource packs, but the picker
     * can offer them because selection adopts their files into the Photon workspace before drawing.
     */
    public static List<ResourceLocation> listLooseTextureIds() {
        var found = new LinkedHashSet<ResourceLocation>();
        for (var namespace : buildIndex().keySet()) {
            if (BUILTIN.contains(namespace)) continue;
            for (var folder : namespaceFolders(namespace)) {
                var textures = folder.resolve("textures");
                if (!Files.isDirectory(textures)) continue;
                try (Stream<Path> files = Files.walk(textures)) {
                    files.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png"))
                            .forEach(path -> {
                                var relative = textures.getParent().relativize(path).toString().replace('\\', '/');
                                var id = ResourceLocation.tryParse(namespace + ":" + relative);
                                if (id != null) found.add(id);
                            });
                } catch (IOException ignored) {
                    // An unreadable asset folder contributes no choices.
                }
            }
        }
        return found.stream().sorted(java.util.Comparator.comparing(ResourceLocation::toString)).toList();
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
