package com.lowdragmc.photon.client.postprocessing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameRetryPolicyTest {
    @Test
    void defersOnlyTheFailedKeyUntilItsRetryFrame() {
        var policy = new FrameRetryPolicy<String>();
        policy.failed("large", 10, 3);

        assertTrue(policy.shouldDefer("large", 10));
        assertTrue(policy.shouldDefer("large", 12));
        assertFalse(policy.shouldDefer("small", 10));
        assertFalse(policy.shouldDefer("large", 13));
    }

    @Test
    void successAndFrameExpiryClearRetryWindows() {
        var policy = new FrameRetryPolicy<String>();
        policy.failed("viewport", 20, 5);
        policy.succeeded("viewport");
        assertFalse(policy.shouldDefer("viewport", 21));

        policy.failed("viewport", 20, 5);
        policy.advanceTo(24);
        assertTrue(policy.shouldDefer("viewport", 24));
        policy.advanceTo(25);
        assertFalse(policy.shouldDefer("viewport", 25));
    }

    @Test
    void rejectsNonPositiveRetryWindows() {
        var policy = new FrameRetryPolicy<String>();
        assertThrows(IllegalArgumentException.class, () -> policy.failed("viewport", 0, 0));
    }
}
