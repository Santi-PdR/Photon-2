package com.lowdragmc.photon.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

/** Shader defaults that Minecraft 1.21 moved into ShaderInstance#setDefaultUniforms. */
public final class PhotonShaderUtils {
    private PhotonShaderUtils() {
    }

    public static void applyDefaultUniforms(ShaderInstance shader) {
        shader.safeGetUniform("ModelViewMat").set(RenderSystem.getModelViewMatrix());
        shader.safeGetUniform("ProjMat").set(RenderSystem.getProjectionMatrix());
        shader.safeGetUniform("ScreenSize").set(
                (float) Minecraft.getInstance().getWindow().getWidth(),
                (float) Minecraft.getInstance().getWindow().getHeight());
        shader.safeGetUniform("GameTime").set(RenderSystem.getShaderGameTime());
    }
}
