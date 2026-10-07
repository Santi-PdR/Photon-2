package com.lowdragmc.photon.client.gameobject.emitter.data.model.skin;

import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;

/** CPU linear-blend skinning adapted to the Forge mesh's four-corner quad storage. */
public final class SkinDeformer {
    private final Skeleton skeleton;
    private final float[] trs;
    private final float[] world;
    private final float[] skinMatrices;

    public SkinDeformer(Skeleton skeleton) {
        this.skeleton = skeleton;
        int joints = skeleton.jointCount();
        trs = new float[joints * Skeleton.FLOATS_PER_TRS];
        world = new float[joints * Skeleton.FLOATS_PER_MATRIX];
        skinMatrices = new float[joints * Skeleton.FLOATS_PER_MATRIX];
    }

    public Skeleton skeleton() {
        return skeleton;
    }

    public void pose(AnimationClip clip, float time) {
        System.arraycopy(skeleton.restTrs(), 0, trs, 0, trs.length);
        if (clip != null) clip.sample(time, trs);
        float[] inverseBind = skeleton.inverseBind();
        for (int joint = 0; joint < skeleton.jointCount(); joint++) {
            int at = joint * Skeleton.FLOATS_PER_MATRIX;
            Skeleton.fromTrs(world, at, trs, joint * Skeleton.FLOATS_PER_TRS);
            int parent = skeleton.parent(joint);
            if (parent >= 0) {
                Skeleton.multiply(skinMatrices, at, world, parent * Skeleton.FLOATS_PER_MATRIX, world, at);
                System.arraycopy(skinMatrices, at, world, at, Skeleton.FLOATS_PER_MATRIX);
            }
            Skeleton.multiply(skinMatrices, at, world, at, inverseBind, at);
        }
    }

    /** Deform geometry alone, leaving any tangent stream untouched. */
    public void deform(PhotonMesh mesh, MeshSkin skin, float[] out) {
        deform(mesh, skin, out, null, null);
    }

    /** Writes position xyz and normal xyz for each topology corner. */
    public void deform(PhotonMesh mesh, MeshSkin skin, float[] out, float[] outTangents) {
        deform(mesh, skin, out, outTangents == null ? null : mesh.tangents(), outTangents);
    }

    /** Deform positions and normals, and optionally a separately cached tangent frame. */
    public void deform(PhotonMesh mesh, MeshSkin skin, float[] out, float[] restTangents, float[] outTangents) {
        float[] vertices = mesh.vertices();
        int vertexCount = mesh.quadCount() * 4;
        int[] joints = skin.joints();
        float[] weights = skin.weights();
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int src = vertex * PhotonMesh.FLOATS_PER_VERTEX;
            int dst = vertex * 6;
            int influence = vertex * MeshSkin.INFLUENCES;
            float px = vertices[src], py = vertices[src + 1], pz = vertices[src + 2];
            float nx = vertices[src + 5], ny = vertices[src + 6], nz = vertices[src + 7];
            float ox = 0, oy = 0, oz = 0, onx = 0, ony = 0, onz = 0;
            float tx = 0, ty = 0, tz = 0, sum = 0;
            int tangentAt = vertex * PhotonMesh.FLOATS_PER_TANGENT;
            boolean hasInfluence = false;
            for (int i = 0; i < MeshSkin.INFLUENCES && influence + i < joints.length; i++) {
                float weight = weights[influence + i];
                int joint = joints[influence + i];
                if (!(weight > 0) || joint < 0 || joint >= skeleton.jointCount()) continue;
                hasInfluence = true;
                sum += weight;
                int m = joint * Skeleton.FLOATS_PER_MATRIX;
                ox += weight * (skinMatrices[m] * px + skinMatrices[m + 1] * py + skinMatrices[m + 2] * pz + skinMatrices[m + 3]);
                oy += weight * (skinMatrices[m + 4] * px + skinMatrices[m + 5] * py + skinMatrices[m + 6] * pz + skinMatrices[m + 7]);
                oz += weight * (skinMatrices[m + 8] * px + skinMatrices[m + 9] * py + skinMatrices[m + 10] * pz + skinMatrices[m + 11]);
                onx += weight * (skinMatrices[m] * nx + skinMatrices[m + 1] * ny + skinMatrices[m + 2] * nz);
                ony += weight * (skinMatrices[m + 4] * nx + skinMatrices[m + 5] * ny + skinMatrices[m + 6] * nz);
                onz += weight * (skinMatrices[m + 8] * nx + skinMatrices[m + 9] * ny + skinMatrices[m + 10] * nz);
                if (restTangents != null) {
                    tx += weight * (skinMatrices[m] * restTangents[tangentAt] + skinMatrices[m + 1] * restTangents[tangentAt + 1] + skinMatrices[m + 2] * restTangents[tangentAt + 2]);
                    ty += weight * (skinMatrices[m + 4] * restTangents[tangentAt] + skinMatrices[m + 5] * restTangents[tangentAt + 1] + skinMatrices[m + 6] * restTangents[tangentAt + 2]);
                    tz += weight * (skinMatrices[m + 8] * restTangents[tangentAt] + skinMatrices[m + 9] * restTangents[tangentAt + 1] + skinMatrices[m + 10] * restTangents[tangentAt + 2]);
                }
            }
            if (!hasInfluence || sum <= 1.0e-8f) {
                ox = px; oy = py; oz = pz; onx = nx; ony = ny; onz = nz;
                if (restTangents != null) {
                    tx = restTangents[tangentAt]; ty = restTangents[tangentAt + 1]; tz = restTangents[tangentAt + 2];
                }
            } else if (Math.abs(sum - 1f) > 1.0e-6f) {
                float inv = 1f / sum;
                ox *= inv; oy *= inv; oz *= inv; onx *= inv; ony *= inv; onz *= inv; tx *= inv; ty *= inv; tz *= inv;
            }
            float normalLength = onx * onx + ony * ony + onz * onz;
            if (normalLength > 1.0e-12f && Float.isFinite(normalLength)) {
                float inv = 1f / (float) Math.sqrt(normalLength);
                onx *= inv; ony *= inv; onz *= inv;
            } else { onx = nx; ony = ny; onz = nz; }
            out[dst] = ox; out[dst + 1] = oy; out[dst + 2] = oz;
            out[dst + 3] = onx; out[dst + 4] = ony; out[dst + 5] = onz;
            if (outTangents != null && restTangents != null) {
                float tangentLength = tx * tx + ty * ty + tz * tz;
                if (tangentLength > 1.0e-12f && Float.isFinite(tangentLength)) {
                    float inv = 1f / (float) Math.sqrt(tangentLength);
                    outTangents[tangentAt] = tx * inv; outTangents[tangentAt + 1] = ty * inv; outTangents[tangentAt + 2] = tz * inv;
                } else {
                    outTangents[tangentAt] = restTangents[tangentAt];
                    outTangents[tangentAt + 1] = restTangents[tangentAt + 1];
                    outTangents[tangentAt + 2] = restTangents[tangentAt + 2];
                }
                outTangents[tangentAt + 3] = restTangents[tangentAt + 3];
            }
        }
    }
}
