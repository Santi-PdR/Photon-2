package com.lowdragmc.photon.core.mixins.iris;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accessors for the Oculus 1.20.1 pipeline state used by Photon's GL target probe. */
@Mixin(value = IrisRenderingPipeline.class, remap = false)
public interface IrisRenderingPipelineAccessor {
    @Accessor("renderTargets")
    RenderTargets photon$renderTargets();

    @Accessor("isRenderingWorld")
    boolean photon$isRenderingWorld();
}
