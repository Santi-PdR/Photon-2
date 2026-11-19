package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.UISurface;
import com.lowdragmc.photon.client.PhotonShaders;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceMixin {
    @Inject(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V",
            at = @At("TAIL"), require = 0)
    private void photon$countCreated(CallbackInfo ci) {
        PhotonShaders.onShaderInstanceCreated();
    }

    /**
     * Vanilla {@code VertexBuffer.drawWithShader} writes ScreenSize from the game window, then
     * {@link ShaderInstance#apply()} uploads it. A UI in its own OS window (a popped-out or floated
     * editor) draws into an off-screen target of that window's size, where
     * {@code gl_FragCoord / ScreenSize} then samples the scene captures at the wrong texel.
     * Photon 2.2.10 fixed this on 1.21's {@code setDefaultUniforms}; 1.20.1 has no such method, so
     * the same replacement runs at the start of {@code apply}, and only when the uniform still holds
     * the vanilla window size — Photon's own pipeline/FBO values are left alone.
     */
    @Inject(method = "apply()V", at = @At("HEAD"), require = 0)
    private void photon$screenSizeOfSurface(CallbackInfo ci) {
        Uniform screenSize = ((ShaderInstance) (Object) this).getUniform("ScreenSize");
        if (screenSize == null) return;
        var surface = UISurface.current();
        if (surface == null || surface.isMainWindow()) return;
        var window = Minecraft.getInstance().getWindow();
        var values = screenSize.getFloatBuffer();
        if (values == null || values.get(0) != (float) window.getWidth()
                || values.get(1) != (float) window.getHeight()) {
            return;
        }
        screenSize.set((float) surface.framebufferWidth(), (float) surface.framebufferHeight());
    }
}
