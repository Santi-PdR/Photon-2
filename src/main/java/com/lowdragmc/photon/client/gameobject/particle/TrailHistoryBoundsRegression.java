package com.lowdragmc.photon.client.gameobject.particle;

import com.lowdragmc.photon.Photon;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Regression for classic trail retention and the total expanded smoothing budget. */
public final class TrailHistoryBoundsRegression {
    private TrailHistoryBoundsRegression() {
    }

    public static void verify(TrailParticle particle) {
        var history = particle.new TailArray();
        int historySize = TrailParticle.MAX_RAW_TAILS + 64;
        for (int i = 0; i < historySize; i++) {
            history.add(tail(i, i & 1));
        }
        int retained = TrailParticle.MAX_RAW_TAILS - 64;
        history.trimTo(retained);
        check(history.size() == retained, "raw trail history was not trimmed to its retained budget");
        check(history.getPosition(0).x == historySize - retained,
                "raw trail trim discarded recent points instead of the oldest points");
        check(history.getPosition(retained - 1).x == historySize - 1,
                "raw trail trim lost the newest point");

        var raw = particle.new TailArray();
        for (int i = 0; i < TrailParticle.MAX_RAW_TAILS; i++) {
            raw.add(tail(i, i & 1));
        }
        var smoothed = particle.generateSmoothPath(raw, particle.new TailArray(), 0.05f);
        check(smoothed.size() <= TrailParticle.MAX_SMOOTHED_TAILS,
                "per-segment smoothing exceeded the total trail output budget: " + smoothed.size());
        check(smoothed.size() >= raw.size(), "smoothing dropped a raw trail point");
        check(smoothed.getPosition(0).distanceSquared(raw.getPosition(0)) < 1e-8f,
                "smoothing changed the oldest retained endpoint");
        check(smoothed.getPosition(smoothed.size() - 1)
                        .distanceSquared(raw.getPosition(raw.size() - 1)) < 1e-8f,
                "smoothing changed the newest endpoint");
        Photon.LOGGER.info("[trail-history-bound-smoke] classic trail history and smoothed output stay bounded, retaining newest points");
    }

    private static TrailParticle.Tail tail(float x, float y) {
        return new TrailParticle.Tail(new Vector3f(x, y, 0), 1f,
                new Vector4f(1, 1, 1, 1), 1f);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
