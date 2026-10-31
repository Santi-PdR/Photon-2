package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Geometry math shared by CPU and instanced velocity-aligned billboard rendering. */
public final class StretchedBillboardMath {
    private StretchedBillboardMath() {}

    public static Frame compute(Vector3f velocity, Vector3f worldPos, double camX, double camY, double camZ,
                                Vector3f size, Vector3f spaceScale, float lengthScale, float velocityScale) {
        float speed = velocity.length();
        Vector3f right = speed > 1.0e-5f ? new Vector3f((Vector3fc) velocity).div(speed) : new Vector3f(1, 0, 0);
        Vector3f dirToCam = new Vector3f((float) (camX - worldPos.x), (float) (camY - worldPos.y), (float) (camZ - worldPos.z));
        if (dirToCam.lengthSquared() > 1.0e-5f) dirToCam.normalize(); else dirToCam.set(0, 0, 1);

        Vector3f up = new Vector3f();
        dirToCam.cross((Vector3fc) right, up);
        if (up.lengthSquared() < 1.0e-5f) {
            if (Math.abs(right.y) > 0.99f) up.set(0, 0, 1).cross((Vector3fc) right).normalize();
            else up.set(0, 1, 0).cross((Vector3fc) right).normalize();
        } else up.normalize();

        Vector3f forward = new Vector3f();
        right.cross((Vector3fc) up, forward).normalize();
        Matrix3f basis = new Matrix3f(right.x, right.y, right.z, up.x, up.y, up.z, forward.x, forward.y, forward.z);
        Quaternionf rotation = new Quaternionf().setFromNormalized((Matrix3fc) basis);
        float stretchedSizeX = size.x * (lengthScale + speed * velocityScale);
        float offsetAmount = (stretchedSizeX - size.x) * spaceScale.x;
        return new Frame(rotation, stretchedSizeX, right.x * offsetAmount, right.y * offsetAmount, right.z * offsetAmount);
    }

    public record Frame(Quaternionf rotation, float stretchedSizeX, float offsetX, float offsetY, float offsetZ) {}
}

