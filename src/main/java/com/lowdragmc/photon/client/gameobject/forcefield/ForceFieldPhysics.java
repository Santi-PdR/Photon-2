package com.lowdragmc.photon.client.gameobject.forcefield;

import com.lowdragmc.photon.util.MathCompat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Stateless force-field geometry and velocity math, separated from editor/registry lifecycle. */
@OnlyIn(Dist.CLIENT)
final class ForceFieldPhysics {
    private static final float FORCE_SCALE = 0.05f;

    private ForceFieldPhysics() { }

    static float influence(ForceFieldConfig.Shape shape, Vector3f localPosition,
                           float startRange, float endRange) {
        if (endRange <= 0) return 0;
        float radius = switch (shape) {
            case Sphere -> localPosition.length();
            case Hemisphere -> localPosition.y < 0 ? Float.MAX_VALUE : localPosition.length();
            case Cylinder -> Math.max((float) Math.sqrt(localPosition.x * localPosition.x
                    + localPosition.z * localPosition.z), Math.abs(localPosition.y));
            case Box -> Math.max(Math.abs(localPosition.x),
                    Math.max(Math.abs(localPosition.y), Math.abs(localPosition.z)));
        };
        if (radius > endRange) return 0;
        float effectiveStart = Math.min(startRange, endRange);
        return radius <= effectiveStart ? 1f : 1f - (radius - effectiveStart) / (endRange - effectiveStart);
    }

    static void apply(Vector3f localPosition, Matrix4f localToWorld, Vector3f worldVelocity,
                      float particleSize, float dt, float strength,
                      float directionX, float directionY, float directionZ,
                      float gravity, float gravityFocus, float rotationSpeed, float rotationAttraction,
                      float rotationAnchorX, float rotationAnchorZ, float drag,
                      boolean multiplyDragByParticleSize, boolean multiplyDragByParticleVelocity,
                      float endRange) {
        // Directional force, authored along the field's local axes.
        var direction = new Vector3f(directionX, directionY, directionZ);
        if (direction.lengthSquared() > 0) {
            var magnitude = direction.length();
            localToWorld.transformDirection(direction).normalize(magnitude);
            worldVelocity.add(direction.mul(FORCE_SCALE * strength * dt));
        }

        // Gravity: pull toward the focus point (0 = field center, 1 = endRange toward the particle).
        if (gravity != 0 && localPosition.lengthSquared() > 1e-8f) {
            var pull = new Vector3f(localPosition).normalize(gravityFocus * endRange).sub(localPosition);
            if (pull.lengthSquared() > 1e-8f) {
                localToWorld.transformDirection(pull).normalize();
                worldVelocity.add(pull.mul(gravity * FORCE_SCALE * strength * dt));
            }
        }

        // Vortex around the field's local Y axis; random anchors are supplied per field and particle.
        if (rotationSpeed != 0) {
            var radialX = localPosition.x - rotationAnchorX;
            var radialZ = localPosition.z - rotationAnchorZ;
            if (radialX * radialX + radialZ * radialZ > 1e-8f) {
                var tangential = new Vector3f(-radialZ, 0, radialX);
                localToWorld.transformDirection(tangential).normalize(rotationSpeed * FORCE_SCALE);
                worldVelocity.add(new Vector3f(tangential).mul(strength * dt));
                var attraction = MathCompat.clamp(rotationAttraction * strength * dt, 0f, 1f);
                if (attraction > 0) worldVelocity.lerp(tangential, attraction);
            }
        }

        // Drag damping, optionally scaled by particle size and speed.
        if (drag > 0) {
            float coefficient = drag * strength;
            if (multiplyDragByParticleSize) coefficient *= particleSize;
            if (multiplyDragByParticleVelocity) coefficient *= worldVelocity.length();
            worldVelocity.mul(Math.max(0f, 1f - coefficient * FORCE_SCALE * dt));
        }
    }
}
