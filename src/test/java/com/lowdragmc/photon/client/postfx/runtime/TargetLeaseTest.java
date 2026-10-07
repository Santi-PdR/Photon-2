package com.lowdragmc.photon.client.postfx.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private record EqualTarget(String key) {
    }
}
