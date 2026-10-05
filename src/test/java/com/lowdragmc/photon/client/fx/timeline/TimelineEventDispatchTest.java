package com.lowdragmc.photon.client.fx.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineEventDispatchTest {
    @Test
    void executorPermissionRefreshesWithoutClearingEditorGates() {
        var dispatch = new TimelineEventDispatch();

        dispatch.begin(false);
        assertFalse(dispatch.canDispatchSignals());
        assertFalse(dispatch.canDispatchAudio());

        // A later playback on the same runtime can use a normal executor again.
        dispatch.begin(true);
        assertTrue(dispatch.canDispatchSignals());
        assertTrue(dispatch.canDispatchAudio());

        // Explicit editor controls remain in force across playback begin calls.
        dispatch.setSignalsEnabled(false);
        dispatch.setAudioEnabled(false);
        dispatch.begin(false);
        dispatch.begin(true);
        assertFalse(dispatch.canDispatchSignals());
        assertFalse(dispatch.canDispatchAudio());

        dispatch.setSignalsEnabled(true);
        dispatch.setAudioEnabled(true);
        assertTrue(dispatch.canDispatchSignals());
        assertTrue(dispatch.canDispatchAudio());
    }
}
