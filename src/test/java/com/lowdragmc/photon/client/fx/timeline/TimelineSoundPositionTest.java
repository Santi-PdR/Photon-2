package com.lowdragmc.photon.client.fx.timeline;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineSoundPositionTest {
    @Test
    void switchesFromListenerRelativeToMovingPositionalPlayback() {
        var position = new AtomicReference<>(new Vector3f(2f, 3f, 4f));
        var playback = new TimelineSoundPosition(false, position::get);

        assertFalse(playback.isPositional());
        assertNull(playback.getPosition());

        playback.update(position::get);
        assertTrue(playback.isPositional());
        assertPosition(playback.getPosition(), 2f, 3f, 4f);

        position.set(new Vector3f(5f, 6f, 7f));
        assertPosition(playback.getPosition(), 5f, 6f, 7f);
    }

    @Test
    void switchesFromPositionalToListenerRelativeAndDropsOldSource() {
        var position = new AtomicReference<>(new Vector3f(2f, 3f, 4f));
        var playback = new TimelineSoundPosition(true, position::get);

        assertTrue(playback.isPositional());
        assertPosition(playback.getPosition(), 2f, 3f, 4f);

        playback.update(null);
        position.set(new Vector3f(5f, 6f, 7f));

        assertFalse(playback.isPositional());
        assertNull(playback.getPosition());
    }

    private static void assertPosition(Vector3f actual, float x, float y, float z) {
        assertEquals(x, actual.x, 1e-6f);
        assertEquals(y, actual.y, 1e-6f);
        assertEquals(z, actual.z, 1e-6f);
    }
}
