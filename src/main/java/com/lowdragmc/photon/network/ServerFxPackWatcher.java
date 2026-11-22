package com.lowdragmc.photon.network;

import com.lowdragmc.photon.Photon;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Watches {@code <server>/photon/fxpacks/} on the server, so a pack the admin copies in, replaces or deletes reaches
 * the players who are online without anyone typing {@code /photon fxpacks}.
 * <p>
 * The manifest otherwise goes out at login, on a dimension change, on a respawn and on the command: a pack updated
 * while everybody was already in the overworld stayed invisible until one of those happened to each player. Listing
 * one folder once a second from the server thread costs what the client's own {@link
 * com.lowdragmc.photon.client.fx.fxpack.FxPackWatcher} pays for the same job; once the listing has held still for
 * {@link #QUIET_SCANS} scans every online player is offered the current list again, exactly as the command does, and
 * each client compares hashes, downloads what it lacks and reloads by itself.
 * <p>
 * Still first: a pack arriving over SFTP grows for seconds. Offered mid-copy, the scan would reject it as a broken zip
 * or hash a half file the finished one no longer matches, so a change counts only once size and modification time
 * have stopped moving — and several files dropped in one go become one offer, not one per file, since every offer a
 * client finishes with a change costs it a resource reload. A folder that cannot keep still (something rewriting a
 * pack every few seconds) is held to {@link #MAX_OFFERS_PER_MINUTE} offers a minute; the change waits, it is not lost.
 * The client's own {@code server-<sha256>.fxpack} cache copies are ignored: in singleplayer the integrated server
 * shares this folder with the client, and a copy the client just downloaded is not a pack the server publishes.
 */
public final class ServerFxPackWatcher {
    /** How often the folder is listed, in server ticks. */
    static final int SCAN_INTERVAL_TICKS = 20;
    /** Scans in a row the listing has to stay the same before the change is offered: the copy — and the admin — are done. */
    static final int QUIET_SCANS = 3;
    /** A guard against a folder that never settles for long: a client reload every few seconds is worse than a late pack. */
    static final int MAX_OFFERS_PER_MINUTE = 3;
    /** The minute the guard counts over, in scans. */
    static final int OFFER_WINDOW_SCANS = 60 * 20 / SCAN_INTERVAL_TICKS;

    private static final Tracker TRACKER = new Tracker();
    @Nullable
    private static volatile MinecraftServer server;
    private static int ticks;
    private static int offers;
    private static int offerWindowScans;
    /** Whether the quota message was logged for the current wait, so a long wait does not log it every second. */
    private static boolean quotaWarned;

    private ServerFxPackWatcher() {
    }

    /** A server is starting: forget the previous one's folder (singleplayer opens a new server per world). */
    public static void onServerStarting(MinecraftServer starting) {
        TRACKER.reset();
        ticks = 0;
        offers = 0;
        offerWindowScans = 0;
        quotaWarned = false;
        server = starting;
    }

    public static void onServerStopping() {
        server = null;
        TRACKER.reset();
    }

    /** Called every server tick; does nothing until the folder or its contents actually change. */
    public static void tick() {
        var current = server;
        if (current == null) return;
        if (++ticks < SCAN_INTERVAL_TICKS) return;
        ticks = 0;
        if (offerWindowScans > 0 && --offerWindowScans == 0) offers = 0;
        var directory = new File(current.getServerDirectory(), FxPackSync.PACK_DIRECTORY);
        var entries = directory.listFiles(file -> file.isFile() && FxPackSync.isPublishedPackFileName(file.getName()));
        // no folder at all reads as an empty one: whatever was offered from it is gone
        var listing = new HashMap<String, Stamp>();
        if (entries != null) {
            for (var entry : entries) {
                listing.put(entry.getName(), new Stamp(entry.length(), entry.lastModified()));
            }
        }
        boolean allowed = offers < MAX_OFFERS_PER_MINUTE;
        var reason = TRACKER.scan(listing, allowed);
        if (reason != null) {
            if (offers++ == 0) offerWindowScans = OFFER_WINDOW_SCANS;
            quotaWarned = false;
            FxPackSync.onPacksChanged(current, reason);
        } else if (!allowed && TRACKER.owes() && !quotaWarned) {
            quotaWarned = true;
            Photon.LOGGER.warn("Photon FX packs in {} keep changing; the next offer to online players waits for the "
                    + "{}-a-minute limit", directory, MAX_OFFERS_PER_MINUTE);
        }
    }

    /** What one scan noted about a pack file: enough to tell a finished copy from one still being written. */
    record Stamp(long size, long modified) {
    }

    /**
     * The bookkeeping, kept apart from the folder and the server so it can be driven with made-up listings: folder
     * listings go in, "offer the list again, because ..." comes out once a change has held still.
     */
    static final class Tracker {
        /** Name → stamp of every pack at the last offer (the server's start counts as one); null before the first scan. */
        @Nullable
        private Map<String, Stamp> offered;
        /** Name → stamp of every pack in the last listing. */
        private Map<String, Stamp> last = Map.of();
        /** Scans in a row the listing has been identical to the one before. */
        private int quietScans;

        /**
         * One scan of the folder.
         *
         * @param listing the packs in the folder now, by file name
         * @param allowed whether an offer may go out now; a due change is kept until one may
         * @return why the list is offered again now, or null when nothing changed, something is still being written, or
         * the offer has to wait
         */
        @Nullable
        String scan(Map<String, Stamp> listing, boolean allowed) {
            if (offered == null) {
                // the packs present when the server started are offered at login; announcing them again a second
                // after startup would reload every early joiner's resources for nothing
                offered = Map.copyOf(listing);
                last = offered;
                quietScans = 0;
                return null;
            }
            if (listing.equals(last)) {
                quietScans++;
            } else {
                quietScans = 0;
                last = Map.copyOf(listing);
            }
            // a change that changed back (a pack copied in and deleted again) owes nothing
            if (listing.equals(offered)) return null;
            if (quietScans < QUIET_SCANS || !allowed) return null;
            var reason = describe(offered, listing);
            offered = Map.copyOf(listing);
            return reason;
        }

        /** Whether the folder differs from what was last offered (a change settling, or one waiting to be allowed). */
        boolean owes() {
            return offered != null && !last.equals(offered);
        }

        void reset() {
            offered = null;
            last = Map.of();
            quietScans = 0;
        }

        private static String describe(Map<String, Stamp> before, Map<String, Stamp> after) {
            int added = 0;
            int replaced = 0;
            int removed = 0;
            for (var entry : after.entrySet()) {
                var previous = before.get(entry.getKey());
                if (previous == null) {
                    added++;
                } else if (!previous.equals(entry.getValue())) {
                    replaced++;
                }
            }
            for (var name : before.keySet()) {
                if (!after.containsKey(name)) removed++;
            }
            var parts = new ArrayList<String>(3);
            if (added > 0) parts.add(added + " added");
            if (replaced > 0) parts.add(replaced + " replaced");
            if (removed > 0) parts.add(removed + " removed");
            return String.join(", ", parts);
        }
    }
}
