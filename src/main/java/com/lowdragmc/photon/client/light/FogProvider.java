package com.lowdragmc.photon.client.light;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Asked once per frame, before culling, for the fog volumes it wants that frame. */
@OnlyIn(Dist.CLIENT)
@FunctionalInterface
public interface FogProvider {
    void submitFog(FogSink sink, float partialTick);
}
