package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Pure geometry shared by the CPU beam path and its instanced shader counterpart. */
final class BeamGeometry {
    private static final float MIN_DIRECTION_LENGTH_SQUARED = 1.0e-12f;
    private static final float MIN_SIDE_LENGTH_SQUARED = 1.0e-12f;

    private BeamGeometry() {
    }

    static boolean hasLength(Vector3fc from, Vector3fc end) {
        return new Vector3f(end).sub(from).lengthSquared() > MIN_DIRECTION_LENGTH_SQUARED;
    }

    /** Camera-relative start crossed with direction, with a stable axis when the view is collinear. */
    static Vector3f side(Vector3fc cameraRelativeStart, Vector3fc direction, float width) {
        var side = new Vector3f(cameraRelativeStart).cross(direction);
        if (side.lengthSquared() <= MIN_SIDE_LENGTH_SQUARED) {
            // Pick an axis not parallel to the beam, preferring world Z for the familiar horizontal case.
            var fallbackAxis = Math.abs(direction.z()) < direction.length() * 0.9f
                    ? new Vector3f(0, 0, 1)
                    : new Vector3f(0, 1, 0);
            side = fallbackAxis.cross(direction);
        }
        return side.normalize().mul(width);
    }
}
