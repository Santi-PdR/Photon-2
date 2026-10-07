package com.lowdragmc.photon.command;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Shared removal policy for entity/block effect command caches. */
final class EffectRemoval {
    private EffectRemoval() {
    }

    static <K, V> void removeMatching(Iterable<K> targets, Map<K, List<V>> cache,
                                      Predicate<? super V> matches, Consumer<? super V> onRemove) {
        for (K target : targets) {
            var effects = cache.get(target);
            if (effects == null) continue;
            Iterator<V> iterator = effects.iterator();
            while (iterator.hasNext()) {
                V effect = iterator.next();
                if (matches.test(effect)) {
                    iterator.remove();
                    onRemove.accept(effect);
                }
            }
            if (effects.isEmpty()) {
                cache.remove(target);
            }
        }
    }
}
