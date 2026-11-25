package com.lowdragmc.photon.client.fx.fxpack;

import com.lowdragmc.photon.Photon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Watches {@code <gameDir>/photon/fxpacks/} so a pack that is copied in — or re-exported by the editor, or replaced by
 * a newer download — becomes usable without the player remembering to press F3+T.
 * <p>
 * Photon mounts fx packs through a {@code RepositorySource}, which Minecraft only re-runs on a resource reload, so a
 * file appearing in that folder is invisible until then. A per-second directory listing is cheap (one folder, a
 * handful of files) and is the only way to notice a copy that was not done by the game itself. What it notices is
 * mounted, remounted or unmounted in the running resource manager ({@link FxPackHotMount}) — no reload, nothing on
 * screen disturbed; a resource reload is the fallback for a manager that cannot be edited, which no vanilla or Forge
 * client has.
 * <p>
 * Three deliberate silences:
 * <ul>
 *   <li>a pack that is still being written is not "new" yet: the file must keep the same size and modification time
 *       across two scans, so a half-copied zip never gets mounted.</li>
 *   <li>nothing is mounted while a reload is running, and a change is forgotten the moment it is served — never
 *       earlier, never twice — so a broken pack cannot put the client into a loop and a change noticed during a
 *       loading screen is still served once the world is up.</li>
 *   <li>{@code server-*} packs belong to the server sync session, which mounts them by itself; unzipped pack folders
 *       are mounted on the next reload but not watched, there being no cheap way to tell a finished folder copy.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class FxPackWatcher {
    /** How often the folder is listed. One second is well under the time it takes to alt-tab back into the game. */
    static final int SCAN_INTERVAL_TICKS = 20;
    /** A guard against a pathological folder: a mount loop is worse than a late notification. */
    static final int MAX_RELOADS_PER_MINUTE = 3;
    /** The minute the guard counts over, in scans: {@link #tick} counts it down once per scan, not once per tick. */
    static final int RELOAD_WINDOW_SCANS = 60 * 20 / SCAN_INTERVAL_TICKS;
    /** Packs the server sync session writes and mounts by itself. */
    static final String SERVER_PACK_PREFIX = "server-";

    private static final Tracker TRACKER = new Tracker();
    private static int ticks;
    private static int reloads;
    private static int reloadWindowScans;
    /** Whether the reload this class fell back to is still running. */
    private static boolean reloadPending;
    /** Whether the quota message was logged for the current wait, so a long wait does not log it every second. */
    private static boolean quotaWarned;
    /** Times the folder's changes were served in place, and times a resource reload had to be asked for instead. */
    private static volatile int hotMounts;
    private static volatile int fullReloads;

    private FxPackWatcher() {
    }

    /** Called every client tick; does nothing at all until the folder or its contents actually change. */
    public static void tick() {
        if (++ticks < SCAN_INTERVAL_TICKS) return;
        ticks = 0;
        if (reloadWindowScans > 0 && --reloadWindowScans == 0) reloads = 0;
        var dir = FXPacks.getFxPacksDir();
        var entries = dir.listFiles(file -> file.isFile() && file.getName().endsWith(FXPacks.SUFFIX)
                && !file.getName().startsWith(SERVER_PACK_PREFIX));
        // no folder at all reads as an empty one: whatever was mounted from it is gone
        var listing = new HashMap<String, Stamp>();
        if (entries != null) {
            for (var entry : entries) {
                listing.put(entry.getName(), new Stamp(entry.length(), entry.lastModified()));
            }
        }
        var reason = TRACKER.scan(listing, name -> FXPacks.isMounted(new File(dir, name)));
        if (reason != null) {
            serve(reason, listing.keySet());
        }
    }

    /** Times the folder's changes were mounted in the running manager, without a reload. */
    public static int hotMounts() {
        return hotMounts;
    }

    /** Times a resource reload had to be asked for instead; the smoke test expects zero. */
    public static int fullReloads() {
        return fullReloads;
    }

    /**
     * Serves the folder's changes: the packs that appeared or changed are mounted (again) in the running resource
     * manager, the ones that left are unmounted. When that is not possible right now — a reload is running, a world
     * is loading, or the minute's quota is spent — nothing is lost: the tracker keeps the change and the next scan asks
     * again. Only a manager that cannot be edited at all gets the resource reload of old.
     */
    private static void serve(String reason, Set<String> present) {
        if (reloadPending) return;
        if (reloads >= MAX_RELOADS_PER_MINUTE) {
            if (!quotaWarned) {
                quotaWarned = true;
                Photon.LOGGER.warn("fxpacks: {} but packs were already mounted {} times this minute; it is served when the minute is up (or press F3+T)",
                        reason, reloads);
            }
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (!canReloadNow(minecraft)) return;
        quotaWarned = false;
        reloads++;
        reloadWindowScans = RELOAD_WINDOW_SCANS;
        var changed = TRACKER.pendingNames();
        TRACKER.served();
        FxPackHotMount.Result result = null;
        try {
            result = FxPackHotMount.sync(name -> !name.startsWith(SERVER_PACK_PREFIX), present, changed);
        } catch (RuntimeException e) {
            Photon.LOGGER.error("fxpacks: {} but mounting in place failed; reloading resources instead", reason, e);
        }
        if (result != null) {
            hotMounts++;
            Photon.LOGGER.info("fxpacks: {} — served in place, no resource reload", reason);
            return;
        }
        fullReloads++;
        reloadPending = true;
        Photon.LOGGER.info("fxpacks: {} — reloading resources automatically", reason);
        try {
            minecraft.reloadResourcePacks().whenComplete((ignored, failure) -> minecraft.execute(() -> {
                reloadPending = false;
                if (failure != null) {
                    Photon.LOGGER.error("fxpacks: automatic reload after {} failed", reason, failure);
                }
            }));
        } catch (RuntimeException e) {
            // not started at all: the next change gets its chance, this one is not retried (a loop is worse)
            reloadPending = false;
            Photon.LOGGER.error("fxpacks: automatic reload after {} could not start", reason, e);
        }
    }

    /**
     * In a world once it is up, or idle on the title screen — the two places F3+T is harmless. Never while a reload
     * runs (its overlay), a world is loading, or a connection is being made.
     */
    private static boolean canReloadNow(Minecraft minecraft) {
        if (minecraft.getOverlay() != null) return false;
        if (minecraft.level != null) {
            return minecraft.player != null && !(minecraft.screen instanceof ReceivingLevelScreen);
        }
        return minecraft.screen instanceof TitleScreen;
    }

    /** What one scan noted about a pack file: enough to tell a finished copy from one still being written. */
    record Stamp(long size, long modified) {
    }

    /**
     * The bookkeeping, kept apart from the folder and the game so it can be driven with made-up listings: folder
     * listings go in, "a reload is owed" comes out, and a debt stays until a reload covers it.
     */
    static final class Tracker {
        /** Name → stamp of every pack in the last listing. */
        private final Map<String, Stamp> seen = new HashMap<>();
        /** Packs whose stamp was the same in the last two listings: finished writing. */
        private final Set<String> stable = new HashSet<>();
        /** Packs the game serves: what the folder held at the last reload (the game's start counts as one). */
        private final Set<String> mounted = new HashSet<>();
        /** Packs that appeared or changed since that reload. */
        private final Set<String> pending = new LinkedHashSet<>();
        /** Packs the game serves that have left the folder. */
        private final Set<String> gone = new LinkedHashSet<>();
        /** The first scan only records what is already there: those packs were mounted at startup. */
        private boolean baselined;

        /**
         * One scan of the folder.
         *
         * @param listing    the packs in the folder now, by file name
         * @param mountedNow whether the game serves the pack of that name at this moment — a reload Photon did not
         *                   start (F3+T, the editor's reload box, a server pack arriving) settles what it covered
         * @return why a reload is owed now, or null when nothing is (or something is still being written)
         */
        @Nullable
        String scan(Map<String, Stamp> listing, Predicate<String> mountedNow) {
            if (!baselined) {
                // a pack that existed when the game started was mounted by the boot-time pack scan; treating it as new
                // would reload the resources a second after launch for no reason
                seen.putAll(listing);
                stable.addAll(listing.keySet());
                mounted.addAll(listing.keySet());
                baselined = true;
                return null;
            }
            for (var entry : listing.entrySet()) {
                var name = entry.getKey();
                var previous = seen.get(name);
                if (previous == null) {
                    Photon.LOGGER.info("fxpacks: detected new pack {}, waiting for it to finish writing", name);
                    pending.add(name);
                    stable.remove(name);
                } else if (!previous.equals(entry.getValue())) {
                    pending.add(name);
                    stable.remove(name);
                } else {
                    stable.add(name);
                }
            }
            for (var name : seen.keySet()) {
                if (listing.containsKey(name)) continue;
                stable.remove(name);
                pending.remove(name);
                // a pack that left before any reload saw it owes nothing; one the game serves has to be unmounted
                if (mounted.contains(name)) gone.add(name);
            }
            seen.clear();
            seen.putAll(listing);
            // a reload started elsewhere has settled a new pack it mounted and a removed one it let go; a replaced
            // pack cannot be told apart from its old self that way, so that one is reloaded for regardless
            for (var iterator = pending.iterator(); iterator.hasNext(); ) {
                var name = iterator.next();
                if (!mounted.contains(name) && mountedNow.test(name)) {
                    mounted.add(name);
                    iterator.remove();
                }
            }
            for (var iterator = gone.iterator(); iterator.hasNext(); ) {
                var name = iterator.next();
                if (!mountedNow.test(name)) {
                    mounted.remove(name);
                    iterator.remove();
                }
            }
            if (pending.isEmpty() && gone.isEmpty()) return null;
            // every changed or new pack has to be stable, otherwise this is a copy still in progress: one reload for all
            if (!stable.containsAll(pending)) return null;
            if (pending.isEmpty()) return "a pack was removed";
            return gone.isEmpty() ? "a pack was added or replaced" : "packs were added and removed";
        }

        /** The packs that appeared or changed since the last time the folder was served, in the order noticed. */
        Set<String> pendingNames() {
            return new LinkedHashSet<>(pending);
        }

        /** The folder is being served: what it holds now is what the game serves from here on; no debt is left. */
        void served() {
            mounted.clear();
            mounted.addAll(seen.keySet());
            pending.clear();
            gone.clear();
        }

        /** Whether a reload is owed that has not started yet (a change waiting to be stable, or to be allowed). */
        boolean owes() {
            return !pending.isEmpty() || !gone.isEmpty();
        }
    }
}
