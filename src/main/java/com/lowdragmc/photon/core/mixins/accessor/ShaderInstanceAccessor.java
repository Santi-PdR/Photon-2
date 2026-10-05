package com.lowdragmc.photon.core.mixins.accessor;

import com.mojang.blaze3d.shaders.BlendMode;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ShaderInstance.class)
public interface ShaderInstanceAccessor {
    @Accessor("samplerNames")
    List<String> photon$getSamplerNames();

    @Accessor("blend")
    BlendMode photon$getBlend();

    @Mutable
    @Accessor("blend")
    void photon$setBlend(BlendMode blendMode);
}
