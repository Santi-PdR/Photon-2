package com.lowdragmc.photon.client.gameobject.particle.renderer;

/** Point-count checks shared by the CPU and instanced trail paths. */
final class TrailGeometry {
    private TrailGeometry() {}

    static boolean hasSegments(int tailCount, boolean pushesHead) {
        return tailCount + (pushesHead ? 1 : 0) > 1;
    }
}
