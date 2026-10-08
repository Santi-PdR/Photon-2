package com.lowdragmc.photon.client.postfx.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetLeaseTest {
    @Test
    void releasesUntransferredIdentitiesOnceAndLeavesTransferredTargetsAlone() {
        var released = new ArrayList<EqualTarget>();
        var lease = new TargetLease<EqualTarget>(released::add);
        var releasedNow = new EqualTarget("same");
        var transferred = new EqualTarget("same");
        var exceptionOwned = new EqualTarget("other");

        lease.acquire(releasedNow);
        lease.acquire(releasedNow);
        lease.acquire(transferred);
        lease.acquire(exceptionOwned);
        lease.release(releasedNow);
        lease.transfer(transferred);
        lease.close();

        assertEquals(2, released.size());
        assertSame(releasedNow, released.get(0));
        assertSame(exceptionOwned, released.get(1));
    }

    @Test
    void releasesOwnedTargetsWhenTheRenderOperationThrows() {
        var released = new ArrayList<EqualTarget>();
        var target = new EqualTarget("target");

        assertThrows(IllegalStateException.class, () -> {
            try (var lease = new TargetLease<EqualTarget>(released::add)) {
                lease.acquire(target);
                throw new IllegalStateException("simulated blit failure");
            }
        });

        assertEquals(List.of(target), released);
    }

    @Test
    void attemptsEveryReleaseEvenWhenOnePoolReturnFails() {
        var released = new ArrayList<EqualTarget>();
        var first = new EqualTarget("first");
        var second = new EqualTarget("second");

        assertThrows(IllegalStateException.class, () -> {
            try (var lease = new TargetLease<EqualTarget>(target -> {
                released.add(target);
                if (target == first) throw new IllegalStateException("simulated pool failure");
            })) {
                lease.acquire(first);
                lease.acquire(second);
            }
        });

        assertEquals(2, released.size());
    }

    @Test
    void preservesAnEarlierRenderFailureWhileReturningEveryLease() {
        var attempted = new ArrayList<EqualTarget>();
        var primaryFailure = new IllegalStateException("render-state restore failed");
        var firstCleanupFailure = new IllegalArgumentException("first lease return failed");
        var secondCleanupFailure = new IllegalStateException("second lease return failed");
        var first = new EqualTarget("first");
        var second = new EqualTarget("second");
        var lease = new TargetLease<EqualTarget>(target -> {
            attempted.add(target);
            if (target == first) throw firstCleanupFailure;
            if (target == second) throw secondCleanupFailure;
        });
        lease.acquire(first);
        lease.acquire(second);

        lease.close(primaryFailure);

        var suppressed = List.of(primaryFailure.getSuppressed());
        assertEquals(2, suppressed.size());
        assertTrue(suppressed.contains(firstCleanupFailure));
        assertTrue(suppressed.contains(secondCleanupFailure));
        assertEquals(2, attempted.size());
    }

    private record EqualTarget(String key) {
    }
}
