package com.lowdragmc.photon.client.compat.iris;

/** Shared loader predicate for the Iris API and its Forge 1.20.1 Oculus implementation. */
public final class ShaderModPresence {
    private ShaderModPresence() {
    }

    public static boolean isLoaded(boolean irisLoaded, boolean oculusLoaded) {
        return irisLoaded || oculusLoaded;
    }
}
