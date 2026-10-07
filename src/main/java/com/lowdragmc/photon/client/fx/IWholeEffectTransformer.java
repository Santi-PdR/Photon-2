package com.lowdragmc.photon.client.fx;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.lowdragmc.photon.client.gameobject.particle.renderer.ParticleRotationMath;

/** Optional render-time transform used only by standalone whole-FX executors. */
public interface IWholeEffectTransformer {
    Quaternionf applyWholeEffectRotation(Quaternionf particleRotation);

    /** Preserve authored animation in the frame carried by the whole effect. */
    default Quaternionf applyAnimatedBillboardRotation(Quaternionf facing, Vector3f animation) {
        return applyWholeEffectRotation(ParticleRotationMath.withParticleRotation(facing, animation));
    }

    /** Default camera billboards retain their authored rotation but not the whole-FX orientation. */
    default Quaternionf applyAnimatedBillboardRotation(Quaternionf facing, Vector3f animation,
                                                       boolean keepCameraFacing) {
        return keepCameraFacing
                ? ParticleRotationMath.withParticleRotation(facing, animation)
                : applyAnimatedBillboardRotation(facing, animation);
    }

    /** The authored particle rotation is inside its orientation; the whole-FX rotation stays outermost. */
    default Quaternionf applyAnimatedModelRotation(Quaternionf spaceRotation, Vector3f animation,
                                                   boolean spaceContainsWholeRotation) {
        var referenceSpace = new Quaternionf(spaceRotation);
        if (spaceContainsWholeRotation) {
            referenceSpace = applyWholeEffectRotation(new Quaternionf()).invert().mul(referenceSpace);
        }
        return applyWholeEffectRotation(ParticleRotationMath.withParticleRotation(referenceSpace, animation));
    }

    Vector3f applyWholeEffectPosition(Vector3f worldPosition);

    /** Rotate a direction without applying the effect's pivot/translation. */
    default Vector3f applyWholeEffectDirection(Vector3f worldDirection) {
        return applyWholeEffectRotation(new Quaternionf()).transform(worldDirection);
    }

    /** Undo the rigid render transform, including its pivot translation. */
    default Vector3f removeWholeEffectPosition(Vector3f renderedPosition) {
        var translation = applyWholeEffectPosition(new Vector3f());
        return applyWholeEffectRotation(new Quaternionf()).invert().transform(renderedPosition.sub(translation));
    }
}
