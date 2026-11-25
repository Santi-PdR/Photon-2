package com.lowdragmc.photon.client.util;

import com.lowdragmc.photon.Photon;

/** Regression for persisted managed-list counts that otherwise drive unbounded object-creation loops. */
public final class LDLibPersistenceCompatRegression {
    private LDLibPersistenceCompatRegression() {
    }

    public static void verify() {
        check(LDLibPersistenceCompat.checkedManagedListCount(0, "test") == 0,
                "an empty managed list was rejected");
        check(LDLibPersistenceCompat.checkedManagedListCount(
                        LDLibPersistenceCompat.MAX_MANAGED_LIST_ENTRIES, "test")
                        == LDLibPersistenceCompat.MAX_MANAGED_LIST_ENTRIES,
                "the managed-list size limit was not inclusive");
        expectRejected(-1);
        expectRejected(LDLibPersistenceCompat.MAX_MANAGED_LIST_ENTRIES + 1);
        expectRejected(Integer.MAX_VALUE);
        Photon.LOGGER.info("[managed-list-bounds-smoke] persisted material, burst and sub-emitter lists reject unsafe counts");
    }

    private static void expectRejected(int count) {
        try {
            LDLibPersistenceCompat.checkedManagedListCount(count, "test");
            throw new IllegalStateException("unsafe managed-list count was accepted: " + count);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
