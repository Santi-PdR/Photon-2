package com.lowdragmc.photon.client.fx.fxpack;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.LooseTextures;
import com.lowdragmc.photon.client.fx.timeline.SoundLengthCache;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMeshCache;
import com.lowdragmc.photon.client.util.PhotonTextureCatalogue;
import com.lowdragmc.photon.core.mixins.accessor.FallbackPackEntryAccessor;
import com.lowdragmc.photon.core.mixins.accessor.FallbackResourceManagerAccessor;
import com.lowdragmc.photon.core.mixins.accessor.FilePackResourcesAccessor;
import com.lowdragmc.photon.core.mixins.accessor.MultiPackResourceManagerAccessor;
import com.lowdragmc.photon.core.mixins.accessor.ReloadableResourceManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Mounts and unmounts {@code .fxpack} files in the <b>running</b> resource manager, so a pack that arrives from a
 * server, is copied into {@code photon/fxpacks}, or is replaced there, is served at once — no resource reload, no
 * F3+T, no loading screen, no sound engine restart, nothing else on screen disturbed.
 * <p>
 * How: a reload builds one {@code MultiPackResourceManager} holding, per namespace, a stack of packs lowest priority
 * first, and every lookup afterwards goes through those two tables. This class rebuilds exactly the namespace stacks a
 * pack belongs to — a new stack with the pack at the bottom, or without it — and swaps the tables in whole, so a
 * lookup on another thread sees the old tables or the new ones, never an edit in progress. The pack sits where a
 * reload would put it: at the very bottom, below vanilla, mods, user resource packs and the workspace (see
 * {@link FXPacks}), so it only answers for what nothing else provides. The next real reload rebuilds everything from
 * the folder as before, so the two paths agree.
 * <p>
 * What a reload would also have done is done by hand for the content the pack carries: loaded effects are forgotten
 * ({@link FXHelper#invalidate}), textures already uploaded under its ids are dropped so the next bind reads the new
 * file, models are reparsed, and its {@code sounds.json} events and {@code .ogg} files are put into (or taken out of)
 * the sound manager ({@link FxPackSounds}). Running effects keep playing with what they loaded; the next play reads
 * the new content.
 * <p>
 * Limits, by design: resources only a reload can integrate — textures stitched into an atlas, shader programs
 * registered at reload, language files — keep needing one; fx packs do not carry those.
 */
@OnlyIn(Dist.CLIENT)
public final class FxPackHotMount {
    /** Prefix of the resource-pack id every fx pack of the folder gets, mounted by a reload or by this class. */
    static final String PACK_ID_PREFIX = "fxpack/";

    private static final AtomicInteger RELOADS_SEEN = new AtomicInteger();
    /** Work waiting for the manager to be editable (no reload running). Game thread only. */
    private static final Deque<Runnable> WHEN_EDITABLE = new ArrayDeque<>();
    /** Work scheduled for a later tick. Game thread only. */
    private static final List<Delayed> LATER = new ArrayList<>();

    private record Delayed(int dueTick, Runnable task) {
    }

    private static int ticks;

    private FxPackHotMount() {
    }

    /** What one {@link #sync} did, by file name. */
    public record Result(List<String> mounted, List<String> unmounted) {
        static final Result NOTHING = new Result(List.of(), List.of());

        public boolean changedNothing() {
            return mounted.isEmpty() && unmounted.isEmpty();
        }
    }

    /** How many resource reloads Photon's reload listener has seen since the game started, whoever asked for them. */
    public static int reloadsSeen() {
        return RELOADS_SEEN.get();
    }

    /** From Photon's reload listener: the manager was rebuilt from the folder, so hot-mount bookkeeping is void. */
    public static void onResourceReload() {
        RELOADS_SEEN.incrementAndGet();
        FxPackSounds.onResourceReload();
    }

    /**
     * Run {@code task} now if the manager is editable, otherwise on the first tick it is (a reload that is running
     * mounts the folder's packs itself, so waiting for it loses nothing). Game thread only.
     */
    public static void whenEditable(Runnable task) {
        if (Minecraft.getInstance().getOverlay() == null) {
            task.run();
        } else {
            WHEN_EDITABLE.add(task);
        }
    }

    /** Run {@code task} in {@code delayTicks} client ticks. Game thread only. */
    static void later(int delayTicks, Runnable task) {
        LATER.add(new Delayed(ticks + Math.max(1, delayTicks), task));
    }

    /** Called every client tick. */
    public static void tick() {
        ticks++;
        if (!LATER.isEmpty()) {
            var due = new ArrayList<Delayed>();
            for (var iterator = LATER.iterator(); iterator.hasNext(); ) {
                var delayed = iterator.next();
                if (delayed.dueTick > ticks) continue;
                iterator.remove();
                due.add(delayed);
            }
            for (var delayed : due) {
                run(delayed.task);
            }
        }
        if (WHEN_EDITABLE.isEmpty() || Minecraft.getInstance().getOverlay() != null) return;
        Runnable task;
        while ((task = WHEN_EDITABLE.poll()) != null) {
            run(task);
            if (Minecraft.getInstance().getOverlay() != null) return;
        }
    }

    private static void run(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            Photon.LOGGER.error("fxpacks: deferred work failed", e);
        }
    }

    /**
     * Make the running resource manager serve, of the {@code .fxpack} files directly in {@code photon/fxpacks} whose
     * name {@code scope} accepts, exactly {@code wanted}: the missing ones are mounted, the others unmounted, and
     * those in {@code replaced} — a mounted pack whose file changed underneath — are unmounted and mounted again from
     * the new file. Files outside {@code scope} are left exactly as they are. Game thread only.
     *
     * @return what was done, or null when the live manager is not vanilla's and cannot be edited: the caller falls
     *         back to a resource reload. Call {@link #whenEditable} first; while a reload runs this throws.
     */
    @Nullable
    public static Result sync(Predicate<String> scope, Set<String> wanted, Set<String> replaced) {
        var minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) throw new IllegalStateException("fx packs are mounted on the game thread");
        if (minecraft.getOverlay() != null) throw new IllegalStateException("a resource reload is running");
        var live = liveManager(minecraft);
        if (live == null) return null;
        try {
            return syncInto(live, scope, wanted, replaced);
        } catch (RuntimeException e) {
            Photon.LOGGER.error("fxpacks: could not mount packs in the running resource manager", e);
            return null;
        }
    }

    @Nullable
    private static MultiPackResourceManager liveManager(Minecraft minecraft) {
        if (!(minecraft.getResourceManager() instanceof ReloadableResourceManager reloadable)) return null;
        return ((ReloadableResourceManagerAccessor) reloadable).photon$getResources()
                instanceof MultiPackResourceManager live ? live : null;
    }

    private static Result syncInto(MultiPackResourceManager live, Predicate<String> scope, Set<String> wanted,
                                   Set<String> replaced) {
        var access = (MultiPackResourceManagerAccessor) live;
        var directory = FXPacks.getFxPacksDir();
        // the folder's packs the manager serves now, within scope, by file name
        var mounted = new LinkedHashMap<String, FilePackResources>();
        for (var pack : access.photon$getPacks()) {
            if (!(pack instanceof FilePackResources filePack)) continue;
            var file = ((FilePackResourcesAccessor) filePack).photon$getFile();
            if (FXPacks.isLoadable(file) && scope.test(file.getName())) mounted.put(file.getName(), filePack);
        }
        var toUnmount = new ArrayList<FilePackResources>();
        for (var entry : mounted.entrySet()) {
            if (!wanted.contains(entry.getKey()) || replaced.contains(entry.getKey())) toUnmount.add(entry.getValue());
        }
        var toMount = new ArrayList<String>();
        for (var name : wanted) {
            if (!scope.test(name) || !name.endsWith(FXPacks.SUFFIX)) continue;
            if ((!mounted.containsKey(name) || replaced.contains(name)) && new File(directory, name).isFile()) {
                toMount.add(name);
            }
        }
        if (toUnmount.isEmpty() && toMount.isEmpty()) return Result.NOTHING;

        var packs = new ArrayList<>(access.photon$getPacks());
        var managers = new HashMap<>(access.photon$getNamespacedManagers());
        var touched = new Contents();
        var unmountedNames = new ArrayList<String>();
        for (var pack : toUnmount) {
            touched.addAll(Contents.of(pack));
            FxPackSounds.unregister(pack);
            for (var entry : List.copyOf(managers.entrySet())) {
                if (entry.getValue().listPacks().noneMatch(candidate -> candidate == pack)) continue;
                var rebuilt = without(entry.getKey(), entry.getValue(), pack);
                if (rebuilt == null) managers.remove(entry.getKey());
                else managers.put(entry.getKey(), rebuilt);
            }
            packs.remove(pack);
            unmountedNames.add(((FilePackResourcesAccessor) pack).photon$getFile().getName());
        }
        // a reload orders the folder's packs by id, lowest first; adding each at the bottom in reverse order keeps that
        toMount.sort(null);
        var fresh = new ArrayList<FilePackResources>();
        for (int i = toMount.size() - 1; i >= 0; i--) {
            var name = toMount.get(i);
            var pack = new FilePackResources(PACK_ID_PREFIX + name, new File(directory, name), false);
            var namespaces = pack.getNamespaces(PackType.CLIENT_RESOURCES);
            if (namespaces.isEmpty()) {
                // unreadable, or nothing under assets/: a reload would mount a pack that serves nothing; leave it to one
                pack.close();
                Photon.LOGGER.warn("fxpacks: {} could not be read or has no assets; not mounted", name);
                continue;
            }
            for (var namespace : namespaces) {
                managers.put(namespace, withAtBottom(namespace, managers.get(namespace), pack));
            }
            packs.add(0, pack);
            fresh.add(pack);
        }
        if (toUnmount.isEmpty() && fresh.isEmpty()) return Result.NOTHING;
        // publish: from here on every lookup sees the new stacks
        access.photon$setNamespacedManagers(managers);
        access.photon$setPacks(List.copyOf(packs));
        var mountedNames = new ArrayList<String>();
        for (var pack : fresh) {
            touched.addAll(Contents.of(pack));
            FxPackSounds.register(pack);
            mountedNames.add(((FilePackResourcesAccessor) pack).photon$getFile().getName());
        }
        for (var pack : toUnmount) {
            pack.close();
        }
        touched.invalidate(live);
        Photon.LOGGER.info("fxpacks: mounted {} and unmounted {} in the running resource manager, no reload ({})",
                mountedNames, unmountedNames, touched.summary());
        return new Result(List.copyOf(mountedNames), List.copyOf(unmountedNames));
    }

    /** {@code current}'s stack with {@code pack} at the bottom (a new stack when the namespace is new). */
    private static FallbackResourceManager withAtBottom(String namespace, @Nullable FallbackResourceManager current,
                                                        PackResources pack) {
        var rebuilt = new FallbackResourceManager(PackType.CLIENT_RESOURCES, namespace);
        rebuilt.push(pack);
        if (current != null) {
            ((FallbackResourceManagerAccessor) rebuilt).photon$getFallbacks()
                    .addAll(((FallbackResourceManagerAccessor) current).photon$getFallbacks());
        }
        return rebuilt;
    }

    /** {@code current}'s stack without {@code pack}, or null when nothing is left of it. */
    @Nullable
    private static FallbackResourceManager without(String namespace, FallbackResourceManager current, PackResources pack) {
        var rebuilt = new FallbackResourceManager(PackType.CLIENT_RESOURCES, namespace);
        var entries = ((FallbackResourceManagerAccessor) rebuilt).photon$getFallbacks();
        for (var entry : ((FallbackResourceManagerAccessor) current).photon$getFallbacks()) {
            if (entry instanceof FallbackPackEntryAccessor view && view.photon$getResources() == pack) continue;
            entries.add(entry);
        }
        return entries.isEmpty() ? null : rebuilt;
    }

    /** The ids a pack serves that Photon or vanilla may have cached an answer for. */
    static final class Contents {
        final Set<ResourceLocation> fx = new LinkedHashSet<>();
        final Set<ResourceLocation> textures = new LinkedHashSet<>();
        final Set<ResourceLocation> sounds = new LinkedHashSet<>();
        boolean models;

        static Contents of(PackResources pack) {
            var contents = new Contents();
            for (var namespace : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
                pack.listResources(PackType.CLIENT_RESOURCES, namespace, "fx", (location, supplier) -> {
                    var path = location.getPath();
                    if (!path.endsWith(FX.SUFFIX)) return;
                    var id = location.withPath(path.substring(FXHelper.FX_PATH.length(), path.length() - FX.SUFFIX.length()));
                    if (FXPacks.isSafePackLocation(id)) contents.fx.add(id);
                });
                pack.listResources(PackType.CLIENT_RESOURCES, namespace, "textures",
                        (location, supplier) -> contents.textures.add(location));
                pack.listResources(PackType.CLIENT_RESOURCES, namespace, "sounds",
                        (location, supplier) -> contents.sounds.add(location));
                pack.listResources(PackType.CLIENT_RESOURCES, namespace, "models",
                        (location, supplier) -> contents.models = true);
            }
            return contents;
        }

        void addAll(Contents other) {
            fx.addAll(other.fx);
            textures.addAll(other.textures);
            sounds.addAll(other.sounds);
            models |= other.models;
        }

        String summary() {
            return fx.size() + " fx, " + textures.size() + " textures, " + sounds.size() + " sounds"
                    + (models ? ", models" : "");
        }

        /** Forget what was cached about these ids, the way a reload would have — but only for them. */
        void invalidate(MultiPackResourceManager live) {
            if (!fx.isEmpty()) FXHelper.invalidate(List.copyOf(fx));
            if (!textures.isEmpty()) {
                refreshTextures(textures, live);
                LooseTextures.invalidate(textures);
                PhotonTextureCatalogue.invalidate();
            }
            if (models) PhotonMeshCache.INSTANCE.clear();
            if (!sounds.isEmpty()) SoundLengthCache.clear();
        }

        /**
         * Textures the manager already uploaded under these ids were read from the old file, or were missing: drop
         * them so the next bind reads what is served now. Only plain file textures are touched; a texture another
         * system registered under the id (an atlas, a dynamic texture) is left alone.
         */
        private static void refreshTextures(Collection<ResourceLocation> ids, MultiPackResourceManager live) {
            var textureManager = Minecraft.getInstance().getTextureManager();
            var missing = MissingTextureAtlasSprite.getTexture();
            for (var id : ids) {
                var existing = textureManager.getTexture(id, null);
                if (existing == null) continue;
                if (existing == missing) {
                    // a draw asked for it before the pack arrived and vanilla remembered the failure; register() is
                    // the one path that replaces that entry without releasing the shared missing texture
                    if (live.getResource(id).isPresent()) textureManager.register(id, new SimpleTexture(id));
                } else if (existing instanceof SimpleTexture) {
                    textureManager.release(id);
                }
            }
        }
    }
}
