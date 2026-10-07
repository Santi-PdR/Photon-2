package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Rotation composition shared by the CPU and instanced particle render paths. */
public final class ParticleRotationMath {
    private ParticleRotationMath() {
    }

    /**
     * Converts Unity-style Euler angles (radians) in ZXY order: roll, then pitch, then yaw.
     * JOML's {@code rotateXYZ} uses a different order and changes the result for compound angles.
     */
    public static Quaternionf eulerRotation(Vector3f rotation) {
        return new Quaternionf().rotateY(rotation.y).rotateX(rotation.x).rotateZ(rotation.z);
    }

    /** Applies a particle's authored rotation inside its simulation-space or facing orientation. */
    public static Quaternionf withParticleRotation(Quaternionf outer, Vector3f rotation) {
        return new Quaternionf(outer).mul(eulerRotation(rotation));
    }
}
