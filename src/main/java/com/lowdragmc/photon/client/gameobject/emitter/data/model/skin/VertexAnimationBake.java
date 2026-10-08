package com.lowdragmc.photon.client.gameobject.emitter.data.model.skin;

import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import org.jetbrains.annotations.Nullable;

/** Bakes skinned poses into a frame-major xyz + packed-octahedral-normal table. */
public final class VertexAnimationBake {
    public static final int FLOATS_PER_VERTEX = 4;
    /** Photon 26.2 API name for the same four-float VAT texel layout. */
    public static final int FLOATS_PER_TEXEL = FLOATS_PER_VERTEX;
    public static final int MAX_TEXELS = 2_000_000;
    private static final float OCT_SCALE = 4095f;
    private static final float OCT_STRIDE = 4096f;

    private VertexAnimationBake() {}

    @Nullable
    public static float[] bake(SkinnedModel model, @Nullable AnimationClip clip, int frames) {
        return bake(model, clip, frames, true);
    }

    @Nullable
    public static float[] bake(SkinnedModel model, @Nullable AnimationClip clip, int frames, boolean loop) {
        if (!model.isAnimated() || frames <= 0 || model.mesh().isEmpty()) return null;
        int vertices = model.mesh().quadCount() * 4;
        long texels = (long) frames * vertices;
        if (texels <= 0 || texels > MAX_TEXELS) return null;
        float[] table = new float[(int) texels * FLOATS_PER_VERTEX];
        float[] pose = new float[vertices * 6];
        SkinDeformer deformer = new SkinDeformer(model.skeleton());
        float duration = clip == null ? 0f : clip.duration();
        for (int frame = 0; frame < frames; frame++) {
            float sample = loop || frames == 1 ? (float) frame / frames : (float) frame / (frames - 1);
            deformer.pose(clip, duration * sample);
            deformer.deform(model.mesh(), model.skin(), pose, null);
            int dst = frame * vertices * FLOATS_PER_VERTEX;
            for (int vertex = 0; vertex < vertices; vertex++) {
                int from = vertex * 6, to = dst + vertex * FLOATS_PER_VERTEX;
                table[to] = pose[from];
                table[to + 1] = pose[from + 1];
                table[to + 2] = pose[from + 2];
                table[to + 3] = packNormal(pose[from + 3], pose[from + 4], pose[from + 5]);
            }
        }
        return table;
    }

    /** Resolves a normalized animation phase to table frames without wrapping non-looping clips. */
    public static PlaybackFrame playbackFrame(float phase, int frames, boolean loop, boolean interpolate) {
        if (frames <= 1) return new PlaybackFrame(0, 0, 0);
        float normalized = Float.isFinite(phase) ? phase : 0f;
        if (loop) {
            normalized -= (float) Math.floor(normalized);
            float cursor = normalized * frames;
            int frame = (int) Math.floor(cursor);
            return new PlaybackFrame(frame, (frame + 1) % frames, interpolate ? cursor - frame : 0f);
        }
        normalized = Math.max(0f, Math.min(1f, normalized));
        float cursor = normalized * (frames - 1);
        int frame = (int) Math.floor(cursor);
        int next = Math.min(frame + 1, frames - 1);
        return new PlaybackFrame(frame, next, interpolate ? cursor - frame : 0f);
    }

    public record PlaybackFrame(int frame, int nextFrame, float blend) {}

    public static float packNormal(float nx, float ny, float nz) {
        float sum = Math.abs(nx) + Math.abs(ny) + Math.abs(nz);
        if (sum < 1e-20f || !Float.isFinite(sum)) return packNormal(0, 0, 1);
        float x = nx / sum, y = ny / sum;
        if (nz < 0) {
            float folded = (1 - Math.abs(y)) * (x >= 0 ? 1 : -1);
            y = (1 - Math.abs(x)) * (y >= 0 ? 1 : -1);
            x = folded;
        }
        int qx = Math.round(Math.max(0, Math.min(1, x * .5f + .5f)) * OCT_SCALE);
        int qy = Math.round(Math.max(0, Math.min(1, y * .5f + .5f)) * OCT_SCALE);
        return qx * OCT_STRIDE + qy;
    }

    public static void unpackNormal(float packed, float[] out) {
        float yq = packed % OCT_STRIDE, xq = (float) Math.floor(packed / OCT_STRIDE);
        float x = xq / OCT_SCALE * 2 - 1, y = yq / OCT_SCALE * 2 - 1;
        float z = 1 - Math.abs(x) - Math.abs(y);
        if (z < 0) {
            float folded = (1 - Math.abs(y)) * (x >= 0 ? 1 : -1);
            y = (1 - Math.abs(x)) * (y >= 0 ? 1 : -1);
            x = folded;
        }
        float length = (float) Math.sqrt(x*x + y*y + z*z);
        out[0] = x / length; out[1] = y / length; out[2] = z / length;
    }
}
