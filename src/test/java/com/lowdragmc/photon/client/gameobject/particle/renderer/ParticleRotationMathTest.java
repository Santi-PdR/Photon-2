package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleRotationMathTest {
    @Test
    void usesUnityZxyEulerOrder() {
        var rotation = new Vector3f(0.9f, 0.7f, 0.4f);
        var expected = new Quaternionf().rotateY(rotation.y).rotateX(rotation.x).rotateZ(rotation.z);
        var input = new Vector3f(1.0f, 0.3f, -0.5f);

        var actualVector = ParticleRotationMath.eulerRotation(rotation).transform(new Vector3f(input));
        var expectedVector = expected.transform(new Vector3f(input));
        var xyzVector = new Quaternionf().rotateXYZ(rotation.x, rotation.y, rotation.z)
                .transform(new Vector3f(input));

        assertTrue(actualVector.distance(expectedVector) < 1e-6f);
        assertTrue(actualVector.distance(xyzVector) > 0.1f);
    }

    @Test
    void composesParticleRotationInsideItsSpaceOrientation() {
        var space = new Quaternionf().rotateX(0.6f).rotateZ(-0.35f);
        var particleRotation = new Vector3f(0.8f, -0.45f, 0.25f);
        var expected = new Quaternionf(space).mul(ParticleRotationMath.eulerRotation(particleRotation));
        var reversed = new Quaternionf(ParticleRotationMath.eulerRotation(particleRotation)).mul(space);
        var input = new Vector3f(0.2f, 1.0f, -0.4f);

        var actualVector = ParticleRotationMath.withParticleRotation(space, particleRotation)
                .transform(new Vector3f(input));

        assertTrue(actualVector.distance(expected.transform(new Vector3f(input))) < 1e-6f);
        assertTrue(actualVector.distance(reversed.transform(new Vector3f(input))) > 0.1f);
    }
}
