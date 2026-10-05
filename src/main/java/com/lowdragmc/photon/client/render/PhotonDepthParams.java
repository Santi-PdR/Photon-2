package com.lowdragmc.photon.client.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * Supplies the {@code U_DepthParams} contract used by Photon 26.2 shaders on Forge 1.20.1.
 *
 * <p>Forge 1.20.1 uses forward-Z with an OpenGL NDC depth interval of [-1, 1]. The first two
 * components therefore map a sampled window depth to NDC depth as {@code ndc = depth * 2 - 1};
 * the last two are the positive near/far view distances unprojected from the active camera matrix.
 */
public final class PhotonDepthParams {
    private static final float FALLBACK_NEAR = 0.05f;
    private static final float FALLBACK_FAR = 1000f;

    private PhotonDepthParams() {
    }

    public static Vector4f fromProjection(Matrix4fc projection) {
        var inverse = new Matrix4f(projection).invert();
        float near = eyeDepth(inverse, -1f);
        float far = eyeDepth(inverse, 1f);
        if (!Float.isFinite(near) || near <= 0f) near = FALLBACK_NEAR;
        if (!Float.isFinite(far) || far <= near) far = Math.max(FALLBACK_FAR, near + 1f);
        return new Vector4f(2f, -1f, near, far);
    }

    private static float eyeDepth(Matrix4f inverseProjection, float ndcDepth) {
        var view = inverseProjection.transform(new Vector4f(0f, 0f, ndcDepth, 1f));
        return view.w == 0f ? Float.POSITIVE_INFINITY : -view.z / view.w;
    }
}
