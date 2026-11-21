package com.lowdragmc.photon.command;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Removes matching effects from each cache entry and drops entries that become empty. */
public final class EffectRemoval {
    private EffectRemoval() {
    }

    /**
     * Whether a {@code /photon fx remove ...} drops the effect at once: only when its optional {@code force} argument
     * was given and is true. An unqualified remove lets the effect go the way it was built to — the timeline's release
     * section, then what is showing drains (an infinite or looping particle ends with its emitter rather than living
     * on) — exactly what {@code FXRuntime.destroy(false)} does and what the command has always done upstream. An
     * interim build made every remove immediate, so effects vanished in one frame instead of fading out.
     */
    public static boolean force(boolean hasArgument, boolean argument) {
        return hasArgument && argument;
    }

    static <K, V> void removeMatching(Iterable<K> targets, Map<K, List<V>> cache,
                                     Predicate<? super V> matches, Consumer<? super V> onRemove) {
        for (K target : targets) {
            List<V> effects = cache.get(target);
            if (effects == null) continue;
            Iterator<V> iterator = effects.iterator();
            while (iterator.hasNext()) {
                V effect = iterator.next();
                if (!matches.test(effect)) continue;
                iterator.remove();
                onRemove.accept(effect);
            }
            if (effects.isEmpty()) cache.remove(target);
        }
    }
}

