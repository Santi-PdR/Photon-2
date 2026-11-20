package com.lowdragmc.photon.client.fx;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Which effects spawned which: the effect an executor plays (its {@code roots}, see
 * {@link IEffectExecutor#playingAs()}), then every effect a sub-emitter spawned on the way down to a runtime
 * ({@link FXRuntime#spawnChain()}; ids as {@code namespace:path}, Photon 1's embedded sub-effects as
 * {@code #name}).
 * <p>
 * A sub-emitter that spawns an effect already on that path (an effect spawning itself, or {@code a → b → a})
 * never ends: every particle starts another copy of the chain, so the particle count grows without bound
 * within a second or two and the client freezes. Such a spawn is refused, and so is a chain deeper than
 * {@link #MAX_DEPTH} levels, which bounds the damage a long non-cyclic chain of high-rate emitters can do.
 * No Minecraft state is involved, so the rule can be checked anywhere.
 */
public final class SpawnChain {
    /** Deepest sub-emitter nesting allowed (the top-level effect is depth 0). */
    public static final int MAX_DEPTH = 16;

    private SpawnChain() {
    }

    /** The chain id of a Photon 1 embedded sub-effect (they have names, not locations). */
    public static String legacyId(String name) {
        return "#" + name;
    }

    /** The chain id of an effect file. */
    public static String id(ResourceLocation location) {
        return location.toString();
    }

    /**
     * Why spawning {@code id} from a runtime whose chain is {@code parent}, under an executor playing
     * {@code roots}, must not happen; null when it may.
     */
    @Nullable
    public static String refusal(Collection<String> roots, List<String> parent, String id) {
        if (roots.contains(id) || parent.contains(id)) {
            return "it is already playing further out (" + describe(roots, parent, id) + " would never end)";
        }
        if (parent.size() >= MAX_DEPTH) {
            return "sub-emitters are nested more than " + MAX_DEPTH + " levels deep (" + describe(roots, parent, id) + ")";
        }
        return null;
    }

    /** The chain of a runtime spawned as {@code id} from a runtime whose chain is {@code parent}. */
    public static List<String> extend(List<String> parent, String id) {
        var chain = new ArrayList<String>(parent.size() + 1);
        chain.addAll(parent);
        chain.add(id);
        return List.copyOf(chain);
    }

    /** {@code root -> a -> b -> id}, for the log. */
    public static String describe(Collection<String> roots, List<String> parent, String id) {
        var out = new StringBuilder();
        if (!roots.isEmpty()) out.append(roots.size() == 1 ? roots.iterator().next() : roots.toString());
        for (var step : parent) {
            if (out.length() > 0) out.append(" -> ");
            out.append(step);
        }
        if (out.length() > 0) out.append(" -> ");
        return out.append(id).toString();
    }
}
