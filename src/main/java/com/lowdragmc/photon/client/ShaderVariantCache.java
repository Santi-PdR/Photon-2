package com.lowdragmc.photon.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Closes cached render-thread variants without closing shared base shaders. */
public final class ShaderVariantCache {
    private ShaderVariantCache() {
    }

    @SafeVarargs
    public static <K, V> void clearAndClose(Map<K, V> variants, Consumer<? super V> close, V... retained) {
        Set<V> stale = Collections.newSetFromMap(new IdentityHashMap<>());
        stale.addAll(variants.values());
        if (retained != null) {
            for (var value : retained) {
                if (value != null) stale.remove(value);
            }
        }
        variants.clear();
        stale.forEach(close);
    }
}
