package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.client.gameobject.FXObject;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.Queue;

/** Prepares live timeline post effects before the particle renderer starts its passes. */
@OnlyIn(Dist.CLIENT)
public final class FXPostProcessPreparation {
    private FXPostProcessPreparation() {}

    public static void prepare(Map<ParticleRenderType, Queue<Particle>> particles, float partialTicks) {
        Queue<Particle> roots = particles.get(FXObject.NO_RENDER_RENDER_TYPE);
        if (roots == null) return;
        for (Particle particle : roots) {
            if (!(particle instanceof FXObject root) || !root.isAlive()) continue;
            if (root.getScene() instanceof FXRuntime runtime && runtime.root == root) {
                runtime.timelinePlayer.preparePostProcess(partialTicks);
            }
        }
    }
}

