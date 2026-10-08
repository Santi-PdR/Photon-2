package com.lowdragmc.photon.client.postprocessing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceDisposalTest {
    @Test
    void cleanupAfterFailureKeepsTheOperationFailurePrimary() {
        var operationFailure = new IllegalStateException("target setup failed");
        var cleanupFailure = new IllegalArgumentException("target cleanup failed");

        ResourceDisposal.cleanupAfterFailure(operationFailure, () -> {
            throw cleanupFailure;
        });

        assertEquals(List.of(cleanupFailure), List.of(operationFailure.getSuppressed()));
    }

    @Test
    void attemptsEveryReleaseAndPreservesLaterFailuresAsSuppressed() {
        var attempted = new ArrayList<String>();
        var firstFailure = new IllegalStateException("first release failed");
        var secondFailure = new IllegalArgumentException("second release failed");

        var thrown = assertThrows(IllegalStateException.class, () -> ResourceDisposal.disposeAll(
                List.of("first", "second", "third"), resource -> {
                    attempted.add(resource);
                    if (resource.equals("first")) throw firstFailure;
                    if (resource.equals("second")) throw secondFailure;
                }));

        assertSame(firstFailure, thrown);
        assertEquals(List.of(secondFailure), List.of(thrown.getSuppressed()));
        assertEquals(List.of("first", "second", "third"), attempted);
    }
}
