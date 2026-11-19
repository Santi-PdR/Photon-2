package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Collects what an effect needs before its first frame, for {@link FXHelper#preload} and
 * {@link FXHelper#preloadNow}. FX objects ({@link IFXObject#preload}) and materials ({@code IMaterial#preload})
 * report into it. A synchronous preload serves each request on the spot; an asynchronous one decodes textures
 * off-thread and spreads render-thread work over frames.
 */
@OnlyIn(Dist.CLIENT)
public interface PreloadContext {
    /**
     * A texture the effect binds by location, loaded through the vanilla texture manager. Report the id
     * synchronously; a collection-only diagnostics sink records it but deliberately does not run render jobs.
     */
    void texture(ResourceLocation location);

    /** GL work such as compiling a program variant. Always runs on the render thread, one job per frame slice when asynchronous. */
    void onRenderThread(Runnable job);

    /** Another effect this one spawns, e.g. from a sub-emitter. Each effect is preloaded once per request. */
    void fx(ResourceLocation fxLocation);

    /**
     * Whether the effect's timeline animates {@code object}'s config value at {@code path}, a runtime binding
     * path such as {@code renderer.useGPUInstance}. Both of its states then have to be warmed.
     */
    boolean isAnimated(IFXObject object, String path);

    /** True for a read-only texture inventory that must not compile shaders, decode images or upload GPU resources. */
    default boolean isCollectingTextures() {
        return false;
    }
}
