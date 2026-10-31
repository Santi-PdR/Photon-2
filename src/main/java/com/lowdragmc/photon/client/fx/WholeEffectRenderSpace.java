package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Reference-space conversions shared by whole-effect particle renderers. */
public final class WholeEffectRenderSpace {
    private WholeEffectRenderSpace() {}

    private static IWholeEffectTransformer transformer(TileParticle particle) {
        var executor = particle.getEmitter().getEffectExecutor();
        return particle.getConfig().getSimulationSpace() == ParticleConfig.Space.World
                && executor instanceof IWholeEffectTransformer whole ? whole : null;
    }

    public static Vector3f position(TileParticle particle, Vector3f position) { return position; }
    public static Vector3f direction(TileParticle particle, Vector3f direction) { return direction; }

    public static Quaternionf rotation(TileParticle particle, Quaternionf rotation) {
        var whole = transformer(particle);
        return whole == null ? rotation : whole.applyWholeEffectRotation(new Quaternionf((Quaternionfc) rotation));
    }

    public static Vector3f referencePosition(TileParticle particle, Vector3f worldPosition) {
        var executor = particle.getEmitter().getEffectExecutor();
        Vector3f rendered = position(particle, worldPosition);
        return executor instanceof IWholeEffectTransformer whole
                ? whole.removeWholeEffectPosition(new Vector3f((Vector3fc) rendered)) : rendered;
    }

    public static Vector3f referenceDirection(TileParticle particle, Vector3f worldDirection) {
        var executor = particle.getEmitter().getEffectExecutor();
        Vector3f rendered = direction(particle, worldDirection);
        return executor instanceof IWholeEffectTransformer whole
                ? whole.applyWholeEffectRotation(new Quaternionf()).invert().transform(new Vector3f((Vector3fc) rendered))
                : rendered;
    }

    public static Quaternionf referenceRotation(TileParticle particle, Quaternionf worldRotation) {
        Quaternionf rendered = rotation(particle, worldRotation);
        var executor = particle.getEmitter().getEffectExecutor();
        return executor instanceof IWholeEffectTransformer whole
                ? whole.applyWholeEffectRotation(new Quaternionf()).invert().mul((Quaternionfc) rendered)
                : rendered;
    }
}

