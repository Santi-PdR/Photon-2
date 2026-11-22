package com.lowdragmc.photon.network;

import com.lowdragmc.photon.Photon;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * The server folder watcher's bookkeeping, driven with made-up listings. A pack replaced on the server used to reach
 * the players already online only when each of them logged in again, changed dimension, respawned or somebody typed
 * {@code /photon fxpacks}. {@link ServerFxPackWatcher.Tracker} must offer the list again exactly once per change,
 * only once the change has held still, keep it while the offer quota forbids it, and never for a change that undid
 * itself; and the names the server publishes must exclude the client's own {@code server-<sha256>.fxpack} copies.
 */
public final class ServerFxPackWatcherRegression {
    private ServerFxPackWatcherRegression() {
    }

    public static void verify() {
        var tracker = new ServerFxPackWatcher.Tracker();
        // the packs present when the server started are offered at login: the first scan records them, nothing more
        expect(null, tracker.scan(Map.of("ghouls.fxpack", stamp(100, 1)), true), "baseline");
        expect(null, tracker.scan(Map.of("ghouls.fxpack", stamp(100, 1)), true), "nothing changed");
        if (tracker.owes()) throw new IllegalStateException("an unchanged folder is owed an offer");

        // a replacement arriving over SFTP grows across scans: nothing is offered while it changes, nor until it has
        // held still for QUIET_SCANS scans
        expect(null, tracker.scan(Map.of("ghouls.fxpack", stamp(20, 5)), true), "a replacement being written");
        expect(null, tracker.scan(Map.of("ghouls.fxpack", stamp(60, 6)), true), "a replacement still being written");
        for (int quiet = 1; quiet < ServerFxPackWatcher.QUIET_SCANS; quiet++) {
            expect(null, tracker.scan(Map.of("ghouls.fxpack", stamp(60, 6)), true), "quiet scan " + quiet);
        }
        if (!tracker.owes()) throw new IllegalStateException("a replacement that stopped changing is not owed an offer");
        expect("1 replaced", tracker.scan(Map.of("ghouls.fxpack", stamp(60, 6)), true), "a replacement that held still");
        if (tracker.owes()) throw new IllegalStateException("an offer left a debt behind");
        expect(null, tracker.scan(Map.of("ghouls.fxpack", stamp(60, 6)), true), "after the offer");

        // a pack added and another removed while the quota forbids an offer: the change waits, it is not lost
        for (int scan = 0; scan <= ServerFxPackWatcher.QUIET_SCANS; scan++) {
            expect(null, tracker.scan(Map.of("extra.fxpack", stamp(5, 9)), false), "a due change held by the quota");
        }
        if (!tracker.owes()) throw new IllegalStateException("the tracker forgot a change the quota held back");
        expect("1 added, 1 removed", tracker.scan(Map.of("extra.fxpack", stamp(5, 9)), true), "the held change once allowed");

        // a pack copied in and deleted again before it settled owes nothing
        expect(null, tracker.scan(Map.of("extra.fxpack", stamp(5, 9), "blip.fxpack", stamp(1, 11)), true),
                "a pack about to be deleted again");
        for (int scan = 0; scan <= ServerFxPackWatcher.QUIET_SCANS + 1; scan++) {
            expect(null, tracker.scan(Map.of("extra.fxpack", stamp(5, 9)), true), "the folder back as it was offered");
        }
        if (tracker.owes()) throw new IllegalStateException("a change that undid itself is owed an offer");

        // the folder vanishing reads as every pack removed
        for (int quiet = 0; quiet < ServerFxPackWatcher.QUIET_SCANS; quiet++) {
            expect(null, tracker.scan(Map.of(), true), "the folder gone, settling");
        }
        expect("1 removed", tracker.scan(Map.of(), true), "the folder gone");

        if (ServerFxPackWatcher.OFFER_WINDOW_SCANS * ServerFxPackWatcher.SCAN_INTERVAL_TICKS != 60 * 20) {
            throw new IllegalStateException("the offer quota window is not one minute of scans");
        }
        // what the server publishes and watches: packs by any safe name, but not the client's own cache copies, and
        // not a copy still being written
        if (FxPackSync.isPublishedPackFileName("server-" + "0".repeat(64) + FxPackSync.SUFFIX)
                || !FxPackSync.isPublishedPackFileName("server-ci-server-sync.fxpack")
                || !FxPackSync.isPublishedPackFileName("ghouls.fxpack")
                || FxPackSync.isPublishedPackFileName("ghouls.fxpack.part")
                || !FxPackSync.isClientCacheFileName("server-" + "a".repeat(64) + FxPackSync.SUFFIX)
                || FxPackSync.isClientCacheFileName("server-" + "A".repeat(64) + FxPackSync.SUFFIX)) {
            throw new IllegalStateException("the server publishes client cache copies or refuses a plain pack name");
        }
        Photon.LOGGER.info("[server-fxpack-watcher-smoke] a pack replaced, added or removed on the server is offered to the players online once it holds still, kept through the offer quota and never for a change that undid itself");
    }

    private static ServerFxPackWatcher.Stamp stamp(long size, long modified) {
        return new ServerFxPackWatcher.Stamp(size, modified);
    }

    private static void expect(@Nullable String expected, @Nullable String actual, String when) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new IllegalStateException("server fxpack watcher: " + when + " should offer for " + expected + " but offers for " + actual);
        }
    }
}
