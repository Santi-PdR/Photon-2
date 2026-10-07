package com.lowdragmc.photon.client.fx.timeline;

/** Pure timing policy for settling the last timeline state before a high-rate step finishes playback. */
final class TimelineBoundaryPolicy {
    private TimelineBoundaryPolicy() {
    }

    static boolean shouldEvaluateDuration(double currentTime, double nextTime, double duration,
                                          boolean startDelayed) {
        return !startDelayed && currentTime <= duration && nextTime > duration;
    }
}
