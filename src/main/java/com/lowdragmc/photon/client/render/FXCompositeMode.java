package com.lowdragmc.photon.client.render;

import com.lowdragmc.photon.PhotonConfig;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Photon 26.2's public renderer-setting enum, kept as an adapter to the Forge render pipeline type.
 */
public enum FXCompositeMode {
    INHERIT,
    VANILLA,
    LATE;

    public Component getTranslatedName() {
        return Component.translatable("photon.enum.fx_composite_mode." + name().toLowerCase(Locale.ROOT));
    }

    /** Resolves {@link #INHERIT} using the target pipeline's global renderer configuration. */
    public FXCompositeMode resolve() {
        if (this != INHERIT) return this;
        var configured = PhotonConfig.INSTANCE.fxCompositeMode.get();
        var resolved = fromRenderPipeline(configured);
        return resolved == INHERIT ? VANILLA : resolved;
    }

    public com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.FXCompositeMode toRenderPipeline() {
        return com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.FXCompositeMode.valueOf(name());
    }

    public static FXCompositeMode fromRenderPipeline(
            com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.FXCompositeMode mode) {
        return mode == null ? null : valueOf(mode.name());
    }
}
