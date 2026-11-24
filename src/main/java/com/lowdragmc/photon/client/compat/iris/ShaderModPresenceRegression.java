package com.lowdragmc.photon.client.compat.iris;

import com.lowdragmc.photon.Photon;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Ensures either optional shader loader enables the shared Iris API integration. */
@OnlyIn(Dist.CLIENT)
public final class ShaderModPresenceRegression {
    private ShaderModPresenceRegression() {
    }

    public static void verify() {
        check(!ShaderModPresence.isLoaded(false, false), "the optional bridge loaded without Iris or Oculus");
        check(ShaderModPresence.isLoaded(true, false), "the Iris loader was not recognized");
        check(ShaderModPresence.isLoaded(false, true), "the Oculus loader was not recognized");
        check(ShaderModPresence.isLoaded(true, true), "the shared API was disabled when both loader ids were present");
        Photon.LOGGER.info("[iris-oculus-gate-smoke] optional shader bridge and mixins enable for Iris or Oculus, and stay off when neither is installed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
