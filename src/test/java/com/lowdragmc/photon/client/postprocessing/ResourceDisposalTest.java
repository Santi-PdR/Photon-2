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
    void runsEveryCleanupActionAndKeepsTheOperationFailurePrimary() {
        var attempted = new ArrayList<String>();
        var operationFailure = new IllegalStateException("render operation failed");
        var cleanupFailure = new IllegalArgumentException("framebuffer restore failed");

        ResourceDisposal.runAllPreservingFailure(operationFailure,
                () -> attempted.add("release"),
                () -> { attempted.add("framebuffer"); throw cleanupFailure; },
                () -> attempted.add("viewport"));

        assertEquals(List.of(cleanupFailure), List.of(operationFailure.getSuppressed()));
        assertEquals(List.of("release", "framebuffer", "viewport"), attempted);
    }

    @Test
    void runsEveryCleanupActionAndReportsTheFirstCleanupFailureWhenNoOperationFailed() {
        var attempted = new ArrayList<String>();
        var firstFailure = new IllegalStateException("first cleanup failed");
        var secondFailure = new IllegalArgumentException("second cleanup failed");

        var thrown = assertThrows(IllegalStateException.class, () ->
                ResourceDisposal.runAllPreservingFailure(null,
                        () -> { attempted.add("first"); throw firstFailure; },
                        () -> { attempted.add("second"); throw secondFailure; },
                        () -> attempted.add("third")));

        assertSame(firstFailure, thrown);
        assertEquals(List.of(secondFailure), List.of(thrown.getSuppressed()));
        assertEquals(List.of("first", "second", "third"), attempted);
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

    @Test
    void attemptsEveryReleaseAndKeepsTheRenderFailurePrimary() {
        var attempted = new ArrayList<String>();
        var operationFailure = new IllegalStateException("render pass failed");
        var firstCleanupFailure = new IllegalArgumentException("first target release failed");
        var secondCleanupFailure = new IllegalStateException("second target release failed");

        ResourceDisposal.disposeAllPreservingFailure(List.of("first", "second", "third"), resource -> {
            attempted.add(resource);
            if (resource.equals("first")) throw firstCleanupFailure;
            if (resource.equals("second")) throw secondCleanupFailure;
        }, operationFailure);

        assertEquals(List.of(firstCleanupFailure, secondCleanupFailure),
                List.of(operationFailure.getSuppressed()));
        assertEquals(List.of("first", "second", "third"), attempted);
    }
}
