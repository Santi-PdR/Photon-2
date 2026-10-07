package com.lowdragmc.photon.client.render;

/**
 * Logical point in the frame where a Photon pass belongs. Forge 1.20.1 drains the opaque and
 * translucent stages through its particle-render hooks; {@link #DEFERRED} means only the final
 * composite is delayed until after level rendering.
 */
public enum PhotonStage {
    /** Opaque FX render before translucent world geometry, so water and glass can occlude them. */
    AFTER_OPAQUE_FEATURES,
    /** Translucent FX render in the vanilla translucent-particle slot. */
    AFTER_TRANSLUCENT_PARTICLES,
    /** Draw in the translucent slot into a separate layer and composite after level rendering. */
    DEFERRED;

    /** The last in-level Photon stage; deferred composites are resolved after this stage. */
    public static final PhotonStage LAST = AFTER_TRANSLUCENT_PARTICLES;
}
