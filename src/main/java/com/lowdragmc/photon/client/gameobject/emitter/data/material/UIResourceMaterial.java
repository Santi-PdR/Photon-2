package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.lowdragmc.lowdraglib2.editor.resource.BuiltinPath;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.fx.PreloadContext;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.gui.editor.resource.MaterialResource;
import net.minecraft.client.renderer.ShaderInstance;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A <b>live reference</b> to a material in the resource library. Resolution happens on every access
 * (a cache-map lookup) instead of being latched once: the library's backing instance can be replaced
 * under us — resource edits, the file watcher re-reading a changed file (which also clears the
 * {@code ResourceInstance} cache), pack reloads — and a latched instance would silently freeze this
 * material on a stale object forever. Following the canonical
 * {@code ResourceInstance.getResource} lookup each time keeps every user of this reference in sync
 * with what the resource panel shows and edits.
 */
@LDLRegisterClient(name = "ui_resource_material", registry = "photon:material")
public final class UIResourceMaterial implements IMaterial {
    private static final Set<String> REPORTED_CYCLES = ConcurrentHashMap.newKeySet();

    @Persisted
    private IResourcePath resourcePath = new BuiltinPath("");

    private UIResourceMaterial() {

    }

    public UIResourceMaterial(IResourcePath resourcePath) {
        this.resourcePath = resourcePath;
    }

    /** Never null — an empty {@code BuiltinPath("")} round-trips to null through the path codec. */
    public IResourcePath getResourcePath() {
        if (resourcePath == null) resourcePath = new BuiltinPath("");
        return resourcePath;
    }

    /** The referenced material, re-resolved from the library on every call (cheap cached lookup). */
    public IMaterial getInternalMaterial() {
        var material = MaterialResource.INSTANCE.getResourceInstance().getResource(getResourcePath());
        return material == null ? IMaterial.MISSING : material;
    }

    public IMaterial getRawMaterial() {
        var current = getInternalMaterial();
        if (!(current instanceof UIResourceMaterial)) return current;
        return resolveChain(this, current, UIResourceMaterial::getInternalMaterial);
    }

    /** Cycle-safe indirection resolver, separated so malformed reference chains can be tested without a resource pack. */
    static IMaterial resolveChain(UIResourceMaterial origin, IMaterial current,
                                  Function<UIResourceMaterial, IMaterial> resolve) {
        var visited = Collections.newSetFromMap(new IdentityHashMap<UIResourceMaterial, Boolean>());
        visited.add(origin);
        while (current instanceof UIResourceMaterial reference) {
            if (!visited.add(reference)) {
                var path = reference.getResourcePath().getPathWithType();
                if (REPORTED_CYCLES.add(path)) {
                    Photon.LOGGER.warn("photon: cyclic material resource reference at {}; using the missing material", path);
                }
                return IMaterial.MISSING;
            }
            current = resolve.apply(reference);
        }
        return current == null ? IMaterial.MISSING : current;
    }

    @Override
    public ShaderInstance begin(@Nonnull MaterialContext context) {
        return getRawMaterial().begin(context);
    }

    @Override
    public ShaderInstance begin(@Nonnull MaterialContext context, BlendMode slotBlend, boolean slotOwnsRenderState) {
        return getRawMaterial().begin(context, slotBlend, slotOwnsRenderState);
    }

    @Override
    public void end(@Nonnull MaterialContext context) {
        getRawMaterial().end(context);
    }

    @Override
    public void preload(MaterialContext context, PreloadContext preload) {
        getRawMaterial().preload(context, preload);
    }

    @Override
    public IGuiTexture preview() {
        return getRawMaterial().preview();
    }

    @Override
    public long getUsedChannelMask() {
        return getRawMaterial().getUsedChannelMask();
    }

    @Override
    public boolean usesCustomData() {
        return getRawMaterial().usesCustomData();
    }

    @Override
    public boolean usesSceneColor() {
        return getRawMaterial().usesSceneColor();
    }

    @Override
    public int[] requiredCustomChannels() {
        return getRawMaterial().requiredCustomChannels();
    }

    @Nullable
    @Override
    public MaterialRenderState getPreferredRenderState() {
        return getRawMaterial().getPreferredRenderState();
    }

    @Override
    public UIResourceMaterial copy() {
        return new UIResourceMaterial(getResourcePath());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        UIResourceMaterial that = (UIResourceMaterial) o;
        return Objects.equals(getResourcePath(), that.getResourcePath());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getResourcePath());
    }
}
