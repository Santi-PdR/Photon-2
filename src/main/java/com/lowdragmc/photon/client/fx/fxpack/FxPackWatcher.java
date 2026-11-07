package com.lowdragmc.photon.client.fx.fxpack;

import com.lowdragmc.photon.Photon;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Watches {@code <gameDir>/photon/fxpacks/} so a pack that is copied in — or re-exported by the editor, or replaced by
 * a newer download — becomes usable without the player remembering to press F3+T.
 * <p>
 * Photon mounts fx packs through a {@code RepositorySource}, which Minecraft only re-runs on a resource reload, so a
 * file appearing in that folder is invisible until then. A per-second directory listing is cheap (one folder, a
 * handful of files) and is the only way to notice a copy that was not done by the game itself.
 * <p>
 * Two deliberate silences:
 * <ul>
 *   <li>a pack that is still being written is not "new" yet: the file must keep the same size across two scans, so a
 *       half-copied zip never gets mounted.</li>
 *   <li>the reload is not requested while another one is running, and never twice for the same file, so a broken pack
 *       cannot put the client into a reload loop.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class FxPackWatcher {
    /** How often the folder is listed. One second is well under the time it takes to alt-tab back into the game. */
    private static final int SCAN_INTERVAL_TICKS = 20;
    /** A guard against a pathological folder: a reload loop is worse than a missed notification. */
    private static final int MAX_RELOADS_PER_MINUTE = 3;

    /** Name → size of every pack seen in the last scan, with the size it needs to keep to count as finished. */
    private static final Map<String, Long> SIZES = new HashMap<>();
    private static final Map<String, Long> STABLE = new HashMap<>();
    private static int ticks;
    private static int reloads;
    private static int reloadWindowTicks;
    private static boolean reloadPending;
    /** The first scan only records what is already there: those packs were mounted at startup. */
    private static boolean baselined;

    private FxPackWatcher() {
    }

    /** Called every client tick; does nothing at all until the folder or its contents actually change. */
    public static void tick() {
        if (++ticks < SCAN_INTERVAL_TICKS) return;
        ticks = 0;
        if (reloadWindowTicks > 0 && --reloadWindowTicks == 0) reloads = 0;
        var dir = FXPacks.getFxPacksDir();
        var entries = dir.listFiles(file -> file.isFile() && file.getName().endsWith(FXPacks.SUFFIX));
        if (entries == null) {
            // no folder at all: nothing installed, nothing to watch
            SIZES.clear();
            STABLE.clear();
            return;
        }
        var current = new HashMap<String, Long>(entries.length);
        boolean addedOrChanged = false;
        if (!baselined) {
            // a pack that existed when the game started was mounted by the boot-time pack scan; treating it as new
            // would reload the resources a second after launch for no reason
            for (var entry : entries) {
                if (!entry.getName().startsWith("server-")) current.put(entry.getName(), entry.length());
            }
            SIZES.clear();
            SIZES.putAll(current);
            STABLE.putAll(current);
            baselined = true;
            return;
        }
        for (var entry : entries) {
            var name = entry.getName();
            // server-delivered packs are managed by the sync session, which reloads by itself
            if (name.startsWith("server-")) continue;
            var size = entry.length();
            current.put(name, size);
            var previous = SIZES.get(name);
            if (previous == null) {
                addedOrChanged = true;
                Photon.LOGGER.info("fxpacks: detected new pack {}, waiting for it to finish writing", name);
            } else if (!previous.equals(size)) {
                addedOrChanged = true;
            }
            if (previous != null && previous.equals(size)) {
                STABLE.put(name, size);
            } else {
                STABLE.remove(name);
            }
        }
        var removed = SIZES.keySet().stream().anyMatch(name -> !current.containsKey(name));
        SIZES.clear();
        SIZES.putAll(current);
        if (!addedOrChanged && !removed) return;
        // every changed or new pack has to be stable, otherwise this is a copy still in progress
        var settled = current.entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("server-"))
                .allMatch(entry -> entry.getValue().equals(STABLE.get(entry.getKey())));
        if (!settled) return;
        requestReload(removed ? "a pack was removed" : "a pack was added or replaced");
    }

    /** Asks for one resource reload, refusing when the player is in the middle of something or a reload just ran. */
    private static void requestReload(String reason) {
        if (reloadPending) return;
        if (reloads >= MAX_RELOADS_PER_MINUTE) {
            Photon.LOGGER.warn("fxpacks: {} but {} reloads already ran this minute; press F3+T to see it",
                    reason, reloads);
            return;
        }
        var minecraft = Minecraft.getInstance();
        // a reload while a world is loading is not ours to trigger, and a menu open means the player is busy
        if (minecraft.level == null || minecraft.getOverlay() != null) return;
        reloadPending = true;
        reloads++;
        reloadWindowTicks = 60 * 20;
        Photon.LOGGER.info("fxpacks: {} — reloading resources automatically", reason);
        minecraft.reloadResourcePacks().whenComplete((ignored, failure) -> minecraft.execute(() -> {
            reloadPending = false;
            if (failure != null) {
                Photon.LOGGER.error("fxpacks: automatic reload after {} failed", reason, failure);
            }
        }));
    }

    /**
     * Forget what was seen, e.g. before the game writes a pack itself. The next scan re-baselines silently: a file that
     * is already on disk when this is called is not something to reload for.
     */
    public static void reset() {
        SIZES.clear();
        STABLE.clear();
        ticks = 0;
        baselined = false;
    }
}
