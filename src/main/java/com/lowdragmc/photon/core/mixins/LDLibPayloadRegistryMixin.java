package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib.syncdata.TypedPayloadRegistries;
import com.lowdragmc.photon.integration.PhotonLDLibClientPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers Photon codecs before LDLib 1 freezes its typed-payload accessors. */
@Mixin(value = TypedPayloadRegistries.class, remap = false)
public abstract class LDLibPayloadRegistryMixin {
    @Inject(method = "postInit", at = @At("HEAD"), remap = false)
    private static void photon$registerLegacyAccessors(CallbackInfo ci) {
        PhotonLDLibClientPlugin.registerLegacyAccessors();
    }
}
