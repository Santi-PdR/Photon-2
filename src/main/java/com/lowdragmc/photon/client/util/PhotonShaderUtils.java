package com.lowdragmc.photon.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

/** Shader defaults that Minecraft 1.21 moved into ShaderInstance#setDefaultUniforms. */
public final class PhotonShaderUtils {
    private PhotonShaderUtils() {
    }

    /** ShaderInstance.apply() can replace the blend configured by Photon with its JSON default. */
    public static void applyPreservingBlend(ShaderInstance shader) {
        boolean enabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        int srcRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
        int dstRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int srcAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        int equationRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_BLEND_EQUATION_RGB);
        int equationAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_BLEND_EQUATION_ALPHA);
        try {
            shader.apply();
        } finally {
            if (enabled) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            org.lwjgl.opengl.GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
        }
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
        shader.safeGetUniform("IViewRotMat").set(RenderSystem.getInverseViewRotationMatrix());
        shader.safeGetUniform("TextureMat").set(RenderSystem.getTextureMatrix());
        shader.safeGetUniform("GlintAlpha").set(RenderSystem.getShaderGlintAlpha());
        var color = RenderSystem.getShaderColor();
        shader.safeGetUniform("ColorModulator").set(color[0], color[1], color[2], color[3]);
        shader.safeGetUniform("FogStart").set(RenderSystem.getShaderFogStart());
        shader.safeGetUniform("FogEnd").set(RenderSystem.getShaderFogEnd());
        var fog = RenderSystem.getShaderFogColor();
        shader.safeGetUniform("FogColor").set(fog[0], fog[1], fog[2], fog[3]);
        shader.safeGetUniform("FogShape").set(RenderSystem.getShaderFogShape().getIndex());
        // Scene samplers use the draw target's backing texture, which may differ
        // from the window when rendering through an editor or another mod.
        // Match the dynamic uniform set by CustomShaderMaterial.begin(); this
        // default is applied afterwards and must not overwrite it with window size.
        var pipeline = RenderPassPipeline.getCurrent();
        var target = Minecraft.getInstance().getMainRenderTarget();
        shader.safeGetUniform("ScreenSize").set(
                (float) (pipeline != null ? pipeline.framebufferWidth() : target.width),
                (float) (pipeline != null ? pipeline.framebufferHeight() : target.height));
        shader.safeGetUniform("GameTime").set(RenderSystem.getShaderGameTime());
        RenderSystem.setupShaderLights(shader);
    }
}
