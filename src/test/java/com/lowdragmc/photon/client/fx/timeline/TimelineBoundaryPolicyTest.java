package com.lowdragmc.photon.client.fx.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineBoundaryPolicyTest {
    @Test
    void settlesDurationWhenPlaybackStepCrossesIt() {
        assertTrue(TimelineBoundaryPolicy.shouldEvaluateDuration(9, 11, 10, false));
    }

    @Test
    void doesNotDuplicateAnExactDurationEvaluationOnTheNextTick() {
        assertFalse(TimelineBoundaryPolicy.shouldEvaluateDuration(0, 10, 10, false));
    }

    @Test
    void doesNotReevaluateAfterTheClockAlreadyPassedDuration() {
        assertFalse(TimelineBoundaryPolicy.shouldEvaluateDuration(11, 13, 10, false));
    }

    @Test
    void delaysBoundarySettlementUntilTheRootStarts() {
        assertFalse(TimelineBoundaryPolicy.shouldEvaluateDuration(9, 11, 10, true));
    }
}
