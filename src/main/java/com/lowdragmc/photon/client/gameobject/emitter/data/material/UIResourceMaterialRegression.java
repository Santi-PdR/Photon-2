package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.lowdragmc.lowdraglib2.editor.resource.BuiltinPath;

import java.util.IdentityHashMap;

/** Runtime-free regression for self-referential and cyclic material resource wrappers. */
public final class UIResourceMaterialRegression {
    private UIResourceMaterialRegression() {
    }

    public static void verify() {
        var first = new UIResourceMaterial(new BuiltinPath("cycle_probe_a"));
        var second = new UIResourceMaterial(new BuiltinPath("cycle_probe_b"));
        var links = new IdentityHashMap<UIResourceMaterial, IMaterial>();
        links.put(first, second);
        links.put(second, IMaterial.MISSING);
        if (UIResourceMaterial.resolveChain(first, second, links::get) != IMaterial.MISSING) {
            throw new IllegalStateException("A normal material reference chain did not resolve to its terminal material");
        }
        links.put(second, first);
        if (UIResourceMaterial.resolveChain(first, second, links::get) != IMaterial.MISSING) {
            throw new IllegalStateException("A cyclic material resource chain did not fall back safely");
        }
        if (UIResourceMaterial.resolveChain(first, first, links::get) != IMaterial.MISSING) {
            throw new IllegalStateException("A direct self-reference did not fall back safely");
        }
    }
}
