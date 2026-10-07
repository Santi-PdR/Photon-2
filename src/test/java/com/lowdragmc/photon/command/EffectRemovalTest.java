package com.lowdragmc.photon.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class EffectRemovalTest {
    @Test
    void missingCacheEntryDoesNotPreventRemovingEffectsFromLaterTargets() {
        var cache = new HashMap<String, List<String>>();
        cache.put("target-with-effects", new ArrayList<>(List.of("remove", "keep")));
        var removed = new ArrayList<String>();

        EffectRemoval.removeMatching(List.of("target-without-effects", "target-with-effects"), cache,
                "remove"::equals, removed::add);

        assertEquals(List.of("remove"), removed);
        assertEquals(List.of("keep"), cache.get("target-with-effects"));
    }

    @Test
    void removesEmptyCacheEntriesAfterTheLastMatchingEffect() {
        var cache = new HashMap<String, List<String>>();
        cache.put("target", new ArrayList<>(List.of("effect")));

        EffectRemoval.removeMatching(List.of("target"), cache, ignored -> true, ignored -> {});

        assertFalse(cache.containsKey("target"));
        assertNull(cache.get("target"));
    }
}
