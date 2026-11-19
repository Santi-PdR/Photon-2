package com.lowdragmc.photon.client.fx.fxpack;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.network.FxPackSync;

import java.nio.file.Files;

/**
 * The stall watchdog keeps a half-open server sync from hanging the client forever: transfers are
 * driven by request/response, so a server that stops sending would otherwise leave the session
 * pending indefinitely and its packs never active. {@link ClientFxPackSync#sweepStalled} must fail
 * transfers that see no chunk activity and no IO work to explain it, while leaving live ones — and
 * quiet ones whose IO thread is simply busy — alone.
 */
public final class FxPackStallRegression {
    private FxPackStallRegression() {
    }

    public static void verify() throws Exception {
        var now = System.nanoTime();
        long timeout = ClientFxPackSync.STALL_TIMEOUT_NANOS;
        var directory = Files.createTempDirectory("photon-fxpack-stall-");
        var stalledTemp = directory.resolve("stalled.fxpack.part");
        var freshTemp = directory.resolve("fresh.fxpack.part");
        Files.write(stalledTemp, new byte[]{1});
        Files.write(freshTemp, new byte[]{1});
        var session = new ClientFxPackSync.SyncSession(7L, 2);
        var stalled = new ClientFxPackSync.Transfer(
                new FxPackSync.PackEntry("stalled.fxpack", 1, "aa"), directory.resolve("stalled.fxpack"), stalledTemp);
        var fresh = new ClientFxPackSync.Transfer(
                new FxPackSync.PackEntry("fresh.fxpack", 1, "bb"), directory.resolve("fresh.fxpack"), freshTemp);
        session.transfers.put("aa", stalled);
        session.transfers.put("bb", fresh);
        var previous = ClientFxPackSync.activeSession;
        ClientFxPackSync.activeSession = session;
        try {
            // an idle IO thread sweeps the stale transfer and leaves the live one alone
            stalled.lastActivity = now - 2 * timeout;
            ClientFxPackSync.sweepStalled(session, now, now - 2 * timeout);
            if (session.transfers.containsKey("aa")) {
                throw new IllegalStateException("the stalled transfer was not swept");
            }
            if (Files.exists(stalledTemp)) {
                throw new IllegalStateException("the stalled transfer kept its temp file");
            }
            if (!session.transfers.containsKey("bb")) {
                throw new IllegalStateException("the live transfer was swept with the stalled one");
            }
            // a busy IO thread explains the quiet: even a stale transfer is not swept mid-work
            fresh.lastActivity = now - 2 * timeout;
            ClientFxPackSync.sweepStalled(session, now, now);
            if (!session.transfers.containsKey("bb")) {
                throw new IllegalStateException("a transfer was swept while the IO thread was busy");
            }
            // with the IO thread idle again, the stale one goes too and the session completes
            ClientFxPackSync.sweepStalled(session, now, now - 2 * timeout);
            if (!session.transfers.isEmpty()) {
                throw new IllegalStateException("the stale transfer survived the second sweep");
            }
        } finally {
            ClientFxPackSync.activeSession = previous;
            Files.deleteIfExists(stalledTemp);
            Files.deleteIfExists(freshTemp);
        }
        Photon.LOGGER.info("[fxpack-stall-watchdog-smoke] stalled server pack transfers are swept and live ones survive");
    }
}
