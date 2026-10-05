package com.lowdragmc.photon;

import net.minecraftforge.fml.common.Mod;

/** Forge entry point; Photon systems are ported in subsequent implementation steps. */
@Mod(Photon.MOD_ID)
public final class Photon {
    public static final String MOD_ID = "photon";

    public Photon() {
        // Keep common initialization free of client-only class references.
    }
}
