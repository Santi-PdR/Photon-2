package com.lowdragmc.photon.network;

import com.lowdragmc.photon.Photon;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Pins the inactivity and absolute-duration limits used to reclaim partial private-pack uploads. */
@OnlyIn(Dist.CLIENT)
public final class UploadTimeoutRegression {
    private UploadTimeoutRegression() {
    }

    public static void verify() {
        long now = 1_000_000_000_000L;
        check(!FxPackSync.isUploadRequestExpired(now - FxPackSync.UPLOAD_REQUEST_TIMEOUT_NANOS + 1, now),
                "an upload prompt expired before its deadline");
        check(FxPackSync.isUploadRequestExpired(now - FxPackSync.UPLOAD_REQUEST_TIMEOUT_NANOS, now),
                "an unanswered upload prompt did not expire at its deadline");
        check(!FxPackSync.isUploadExpired(now - 1_000_000_000L, now - 1_000_000_000L, now),
                "a recently active upload expired");
        check(FxPackSync.isUploadExpired(now - 1_000_000_000L,
                        now - FxPackSync.UPLOAD_IDLE_TIMEOUT_NANOS, now),
                "an idle partial upload did not expire");
        check(!FxPackSync.isUploadExpired(now - FxPackSync.UPLOAD_MAX_DURATION_NANOS + 1, now, now),
                "an upload exceeded its absolute deadline early");
        check(FxPackSync.isUploadExpired(now - FxPackSync.UPLOAD_MAX_DURATION_NANOS, now, now),
                "a trickle upload bypassed the absolute deadline");
        Photon.LOGGER.info("[private-fx-upload-timeout-smoke] unanswered prompts and stalled or trickle uploads expire at bounded deadlines");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
