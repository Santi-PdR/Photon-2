package com.lowdragmc.photon.client.fx.timeline;

/** Separates per-playback executor permission from editor pause/scrub controls. */
final class TimelineEventDispatch {
    private boolean executorAllowsEvents = true;
    private boolean signalsEnabled = true;
    private boolean audioEnabled = true;

    void begin(boolean executorAllowsEvents) {
        this.executorAllowsEvents = executorAllowsEvents;
    }

    boolean canDispatchSignals() {
        return executorAllowsEvents && signalsEnabled;
    }

    boolean canDispatchAudio() {
        return executorAllowsEvents && audioEnabled;
    }

    boolean audioEnabled() {
        return audioEnabled;
    }

    void setSignalsEnabled(boolean enabled) {
        signalsEnabled = enabled;
    }

    void setAudioEnabled(boolean enabled) {
        audioEnabled = enabled;
    }
}
