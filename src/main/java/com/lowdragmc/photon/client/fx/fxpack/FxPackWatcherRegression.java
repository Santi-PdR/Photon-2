package com.lowdragmc.photon.client.fx.fxpack;

import com.lowdragmc.photon.Photon;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;

/**
 * The folder watcher's bookkeeping, driven with made-up listings. It used to compare each scan with the one before
 * and demand that a changed file already be stable in the same scan — two things that are never true together for a
 * single file — so a copied or re-exported pack was never reloaded for (only a removed one was), and a change seen
 * while a reload could not run was dropped for good. {@link FxPackWatcher.Tracker} must owe exactly one reload per
 * change once the file stops changing, keep that debt until a reload covers it, and tell a same-size replacement
 * from the unchanged file.
 */
public final class FxPackWatcherRegression {
    private FxPackWatcherRegression() {
    }

    public static void verify() {
        var tracker = new FxPackWatcher.Tracker();
        var mounted = new HashSet<String>();
        // the game started with one pack in the folder: the first scan records it without owing anything
        expect(null, tracker.scan(Map.of("old.fxpack", stamp(100, 1)), mounted::contains), "baseline");
        mounted.add("old.fxpack");
        expect(null, tracker.scan(Map.of("old.fxpack", stamp(100, 1)), mounted::contains), "nothing changed");

        // a pack being copied in grows across scans: nothing is owed until it keeps its stamp for two scans
        expect(null, tracker.scan(Map.of("old.fxpack", stamp(100, 1), "new.fxpack", stamp(10, 5)), mounted::contains),
                "a growing pack");
        expect(null, tracker.scan(Map.of("old.fxpack", stamp(100, 1), "new.fxpack", stamp(40, 6)), mounted::contains),
                "a pack still growing");
        expect("a pack was added or replaced",
                tracker.scan(Map.of("old.fxpack", stamp(100, 1), "new.fxpack", stamp(40, 6)), mounted::contains),
                "a copied pack that stopped changing");
        // ... and the debt is not forgotten while the reload cannot run (title screen, quota, another reload)
        expect("a pack was added or replaced",
                tracker.scan(Map.of("old.fxpack", stamp(100, 1), "new.fxpack", stamp(40, 6)), mounted::contains),
                "a debt waiting for the reload to be allowed");
        if (!tracker.owes()) throw new IllegalStateException("the tracker forgot the pack it had not reloaded for");
        tracker.served();
        mounted.add("new.fxpack");
        if (tracker.owes()) throw new IllegalStateException("a reload left a debt behind");
        expect(null, tracker.scan(Map.of("old.fxpack", stamp(100, 1), "new.fxpack", stamp(40, 6)), mounted::contains),
                "after the reload");

        // a replacement with the same size and a newer modification time is a change
        expect(null, tracker.scan(Map.of("old.fxpack", stamp(100, 9), "new.fxpack", stamp(40, 6)), mounted::contains),
                "a same-size replacement, first seen");
        expect("a pack was added or replaced",
                tracker.scan(Map.of("old.fxpack", stamp(100, 9), "new.fxpack", stamp(40, 6)), mounted::contains),
                "a same-size replacement that stopped changing");
        tracker.served();

        // a reload Photon did not start (F3+T, the editor's reload box) settles a new pack it mounted
        expect(null, tracker.scan(Map.of("old.fxpack", stamp(100, 9), "new.fxpack", stamp(40, 6), "box.fxpack", stamp(7, 20)),
                mounted::contains), "a pack the editor exported, first seen");
        mounted.add("box.fxpack");
        expect(null, tracker.scan(Map.of("old.fxpack", stamp(100, 9), "new.fxpack", stamp(40, 6), "box.fxpack", stamp(7, 20)),
                mounted::contains), "a pack mounted by a reload started elsewhere");
        if (tracker.owes()) throw new IllegalStateException("a pack mounted by another reload is still owed one");

        // a pack that leaves owes a reload only if the game serves it; one that never got mounted owes nothing
        expect(null, tracker.scan(Map.of("old.fxpack", stamp(100, 9), "new.fxpack", stamp(40, 6), "box.fxpack", stamp(7, 20),
                "blip.fxpack", stamp(3, 30)), mounted::contains), "a pack that is about to be deleted again");
        expect(null, tracker.scan(Map.of("old.fxpack", stamp(100, 9), "new.fxpack", stamp(40, 6), "box.fxpack", stamp(7, 20)),
                mounted::contains), "a pack deleted before any reload saw it");
        if (tracker.owes()) throw new IllegalStateException("a pack that came and went before any reload is owed one");
        expect("a pack was removed", tracker.scan(Map.of("old.fxpack", stamp(100, 9), "box.fxpack", stamp(7, 20)),
                mounted::contains), "a mounted pack removed");
        // ... and the debt is settled by whichever reload lets it go
        mounted.remove("new.fxpack");
        expect(null, tracker.scan(Map.of("old.fxpack", stamp(100, 9), "box.fxpack", stamp(7, 20)), mounted::contains),
                "a removed pack another reload let go");

        // a removal and a copy at once: one reload, once the copy is complete
        expect(null, tracker.scan(Map.of("old.fxpack", stamp(100, 9), "late.fxpack", stamp(1, 40)), mounted::contains),
                "a removal while another pack is being copied");
        expect("packs were added and removed",
                tracker.scan(Map.of("old.fxpack", stamp(100, 9), "late.fxpack", stamp(1, 40)), mounted::contains),
                "a removal and a finished copy");
        tracker.served();
        // the folder vanishing reads as every pack removed
        expect("a pack was removed", tracker.scan(Map.of(), name -> true), "the folder gone");

        if (FxPackWatcher.RELOAD_WINDOW_SCANS * FxPackWatcher.SCAN_INTERVAL_TICKS != 60 * 20) {
            throw new IllegalStateException("the reload quota window is not one minute of scans");
        }
        Photon.LOGGER.info("[fxpack-watcher-smoke] copied, same-size replaced and removed packs each owe one reload once they stop changing, kept until a reload covers it");
    }

    private static FxPackWatcher.Stamp stamp(long size, long modified) {
        return new FxPackWatcher.Stamp(size, modified);
    }

    private static void expect(@Nullable String expected, @Nullable String actual, String when) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new IllegalStateException("fxpack watcher: " + when + " should owe " + expected + " but owes " + actual);
        }
    }
}
