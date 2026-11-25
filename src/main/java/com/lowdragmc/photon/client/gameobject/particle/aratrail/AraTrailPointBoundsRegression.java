package com.lowdragmc.photon.client.gameobject.particle.aratrail;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.emitter.aratrail.AraTrailConfig;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Guards against malformed authored values producing unbounded point lists or spline work. */
public final class AraTrailPointBoundsRegression {
    private AraTrailPointBoundsRegression() {
    }

    public static void verify() {
        check(AraTrailConfig.safeSmoothness(Integer.MIN_VALUE) == 1, "negative smoothness was not clamped");
        check(AraTrailConfig.safeSmoothness(1) == 1, "minimum smoothness changed");
        check(AraTrailConfig.safeSmoothness(8) == 8, "maximum smoothness changed");
        check(AraTrailConfig.safeSmoothness(Integer.MAX_VALUE) == 8, "oversized smoothness was not clamped");

        var points = new ElasticArray<AraTrailParticle.Point>(AraTrailParticle.Point.class);
        for (int i = 0; i < AraTrailParticle.MAX_POINTS; i++) points.add(point(i));
        var newest = points.get(points.size() - 1);
        check(AraTrailParticle.trimOldestForEmission(points), "full trail did not drop its oldest sample");
        check(points.size() == AraTrailParticle.MAX_POINTS - 1, "trimming left the trail over its point budget");
        check(points.get(0).position.x == 1, "trimming removed a recent point instead of the oldest");
        check(points.get(points.size() - 1) == newest, "trimming changed the current trail head");
        points.add(point(AraTrailParticle.MAX_POINTS));
        check(points.size() == AraTrailParticle.MAX_POINTS, "the next emission exceeded the point budget");
        check(!AraTrailParticle.trimOldestForEmission(new ElasticArray<>(AraTrailParticle.Point.class)),
                "an empty trail was reported as trimmed");
        Photon.LOGGER.info("[aratrail-point-bounds-smoke] malformed smoothness is clamped and trail history stays within 2046 points");
    }

    private static AraTrailParticle.Point point(float x) {
        return new AraTrailParticle.Point(new Vector3f(x, 0, 0), new Vector3f(), new Vector3f(), new Vector3f(),
                new Vector4f(1), 1, x, 1);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
