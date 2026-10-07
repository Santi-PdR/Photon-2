package com.lowdragmc.photon.client.gameobject.forcefield;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForceFieldBehaviorTest {
    @Test
    void sphericalInfluenceFallsOffLinearlyBetweenStartAndEndRange() {
        float influence = ForceFieldPhysics.influence(ForceFieldConfig.Shape.Sphere,
                new Vector3f(5, 0, 0), 0, 10);

        assertEquals(0.5f, influence, 1.0e-6f);
    }

    @Test
    void boxAndHemisphereUseTheirAuthoredInfluenceVolumes() {
        var outsideSphereButInsideBox = new Vector3f(4, 4, 0);

        assertEquals(0, ForceFieldPhysics.influence(ForceFieldConfig.Shape.Sphere,
                outsideSphereButInsideBox, 0, 5), 1.0e-6f);
        assertEquals(0.2f, ForceFieldPhysics.influence(ForceFieldConfig.Shape.Box,
                outsideSphereButInsideBox, 0, 5), 1.0e-6f);
        assertEquals(0, ForceFieldPhysics.influence(ForceFieldConfig.Shape.Hemisphere,
                new Vector3f(0, -1, 0), 0, 5), 1.0e-6f);
    }

    @Test
    void directionalForceScalesWithInfluenceAndStepDuration() {
        var local = new Vector3f(5, 0, 0);
        var velocity = new Vector3f();
        float strength = ForceFieldPhysics.influence(ForceFieldConfig.Shape.Sphere, local, 0, 10);

        ForceFieldPhysics.apply(local, new Matrix4f(), velocity, 1, 2, strength,
                1, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, 10);

        assertEquals(0.05f, velocity.x, 1.0e-6f);
        assertEquals(0, velocity.y, 1.0e-6f);
        assertEquals(0, velocity.z, 1.0e-6f);
    }

    @Test
    void gravityPullsTowardFieldCenter() {
        var local = new Vector3f(2, 0, 0);
        var velocity = new Vector3f();

        ForceFieldPhysics.apply(local, new Matrix4f(), velocity, 1, 1, 1,
                0, 0, 0, 1, 0, 0, 0, 0, 0, 0, false, false, 10);

        assertEquals(-0.05f, velocity.x, 1.0e-6f);
        assertEquals(0, velocity.y, 1.0e-6f);
        assertEquals(0, velocity.z, 1.0e-6f);
    }

    @Test
    void vortexAddsTangentialVelocityAroundTheLocalYAxis() {
        var local = new Vector3f(1, 0, 0);
        var velocity = new Vector3f();

        ForceFieldPhysics.apply(local, new Matrix4f(), velocity, 1, 1, 1,
                0, 0, 0, 0, 0, 1, 0, 0, 0, 0, false, false, 10);

        assertEquals(0, velocity.x, 1.0e-6f);
        assertEquals(0, velocity.y, 1.0e-6f);
        assertEquals(0.05f, velocity.z, 1.0e-6f);
    }

    @Test
    void dragDampsVelocityProportionallyToElapsedTime() {
        var velocity = new Vector3f(1, 0, 0);

        ForceFieldPhysics.apply(new Vector3f(), new Matrix4f(), velocity, 4, 2, 1,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 1, false, false, 10);

        assertEquals(0.9f, velocity.x, 1.0e-6f);
        assertEquals(0, velocity.y, 1.0e-6f);
        assertEquals(0, velocity.z, 1.0e-6f);
    }
}
