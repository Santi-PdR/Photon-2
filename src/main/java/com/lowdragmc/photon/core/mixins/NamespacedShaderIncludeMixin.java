package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.photon.client.shader.NamespacedShaderInclude;
import net.minecraft.FileUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Restores namespaced angle-import support for Photon shaders on Minecraft 1.20.1. */
@Mixin(targets = "net.minecraft.client.renderer.ShaderInstance$1")
public abstract class NamespacedShaderIncludeMixin {
    @Redirect(
            method = "applyImport",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/FileUtil;normalizeResourcePath(Ljava/lang/String;)Ljava/lang/String;"))
    private String photon$normalizeNamespacedInclude(String preprocessedPath) {
        return NamespacedShaderInclude.normalize(preprocessedPath, FileUtil::normalizeResourcePath);
    }
}
