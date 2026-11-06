package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.client.gameobject.particle.renderer.ParticleRotationMath;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Applies one rigid transform to the rendered parts of an effect. */
public interface IWholeEffectTransformer {
    Quaternionf applyWholeEffectRotation(Quaternionf particleRotation);

    default Quaternionf applyAnimatedBillboardRotation(Quaternionf facing, Vector3f animation) {
        return applyWholeEffectRotation(ParticleRotationMath.withParticleRotation(facing, animation));
    }

    default Quaternionf applyAnimatedBillboardRotation(Quaternionf facing, Vector3f animation, boolean keepCameraFacing) {
        return keepCameraFacing ? ParticleRotationMath.withParticleRotation(facing, animation)
                : applyAnimatedBillboardRotation(facing, animation);
    }

    default Quaternionf applyAnimatedModelRotation(Quaternionf spaceRotation, Vector3f animation, boolean spaceContainsWholeRotation) {
        Quaternionf referenceSpace = new Quaternionf((Quaternionfc) spaceRotation);
        if (spaceContainsWholeRotation) {
            referenceSpace = applyWholeEffectRotation(new Quaternionf()).invert().mul((Quaternionfc) referenceSpace);
        }
        return applyWholeEffectRotation(ParticleRotationMath.withParticleRotation(referenceSpace, animation));
    }

    Vector3f applyWholeEffectPosition(Vector3f worldPosition);

    default Vector3f applyWholeEffectDirection(Vector3f worldDirection) {
        return applyWholeEffectRotation(new Quaternionf()).transform(worldDirection);
    }

    default Vector3f removeWholeEffectPosition(Vector3f renderedPosition) {
        Vector3f translation = applyWholeEffectPosition(new Vector3f());
        return applyWholeEffectRotation(new Quaternionf()).invert().transform(renderedPosition.sub((Vector3fc) translation));
    }
}

