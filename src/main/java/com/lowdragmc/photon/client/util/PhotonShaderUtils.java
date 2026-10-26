package com.lowdragmc.photon.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

/** Shader defaults that Minecraft 1.21 moved into ShaderInstance#setDefaultUniforms. */
public final class PhotonShaderUtils {
    private PhotonShaderUtils() {
    }

    public static void applyDefaultUniforms(ShaderInstance shader) {
        // 1.20.1's VertexBuffer.drawWithShader normally supplies these to the shader.
        // Instanced draws bypass VertexBuffer, so copy the same RenderSystem texture
        // bindings (notably Sampler0 and the lightmap in Sampler2) before apply().
        for (int unit = 0; unit < 12; unit++) {
            shader.setSampler("Sampler" + unit, RenderSystem.getShaderTexture(unit));
        }
        shader.safeGetUniform("ModelViewMat").set(RenderSystem.getModelViewMatrix());
        shader.safeGetUniform("ProjMat").set(RenderSystem.getProjectionMatrix());
        var color = RenderSystem.getShaderColor();
        shader.safeGetUniform("ColorModulator").set(color[0], color[1], color[2], color[3]);
        shader.safeGetUniform("FogStart").set(RenderSystem.getShaderFogStart());
        shader.safeGetUniform("FogEnd").set(RenderSystem.getShaderFogEnd());
        var fog = RenderSystem.getShaderFogColor();
        shader.safeGetUniform("FogColor").set(fog[0], fog[1], fog[2], fog[3]);
        shader.safeGetUniform("FogShape").set(RenderSystem.getShaderFogShape().getIndex());
        shader.safeGetUniform("ScreenSize").set(
                (float) Minecraft.getInstance().getWindow().getWidth(),
                (float) Minecraft.getInstance().getWindow().getHeight());
        shader.safeGetUniform("GameTime").set(RenderSystem.getShaderGameTime());
    }
}
