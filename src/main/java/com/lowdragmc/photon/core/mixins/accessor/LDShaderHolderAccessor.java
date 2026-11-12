package com.lowdragmc.photon.core.mixins.accessor;

import com.lowdragmc.lowdraglib2.client.shader.LDShaderHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Access the live sampler assignments, including edits made in the shader configurator. */
@Mixin(value = LDShaderHolder.class, remap = false)
public interface LDShaderHolderAccessor {
    @Accessor(value = "samplerCache", remap = false)
    Map<String, Object> photon$getSamplerCache();
}
