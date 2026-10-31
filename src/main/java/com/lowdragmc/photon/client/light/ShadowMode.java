package com.lowdragmc.photon.client.light;

import net.minecraft.network.chat.Component;


import java.util.Locale;

/** How dynamic lights find their occluders. The light pass reads the ordinal. */
public enum ShadowMode {
    OFF,
    /** The depth buffer only: cheap, but blind to anything off screen. */
    SCREEN,
    /** Rays through a voxel copy of the blocks around each shadowed light, plus short contact shadows. */
    VOXEL;
    public Component getTranslatedName() {
        return Component.translatable("photon.enum.light_shadow_mode." + name().toLowerCase(Locale.ROOT));
    }
}
