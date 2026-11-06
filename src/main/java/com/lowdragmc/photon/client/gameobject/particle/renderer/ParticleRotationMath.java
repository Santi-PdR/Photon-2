package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/** Shared particle Euler composition in Photon/Unity ZXY order. */
public final class ParticleRotationMath {
    private ParticleRotationMath() {}

    public static Quaternionf eulerRotation(Vector3f rotation) {
        return new Quaternionf().rotateY(rotation.y).rotateX(rotation.x).rotateZ(rotation.z);
    }

    public static Quaternionf withParticleRotation(Quaternionf outer, Vector3f rotation) {
        return new Quaternionf((Quaternionfc) outer).mul((Quaternionfc) eulerRotation(rotation));
    }
}

