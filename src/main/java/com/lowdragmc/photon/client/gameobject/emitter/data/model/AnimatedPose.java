package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.lowdragmc.photon.client.gameobject.emitter.data.model.skin.AnimationClip;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.skin.SkinDeformer;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.skin.SkinnedModel;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shares one CPU-deformed pose among emitters using the same model, clip and animation clock. */
final class AnimatedPose {
    private record Key(SkinnedModel model, @Nullable AnimationClip clip, float speed, boolean loop) {
    }

    private static final Map<Key, AnimatedPose> POSES = new ConcurrentHashMap<>();
    private static final int CROWDED = 16;
    private static final long STALE_NANOS = 5_000_000_000L;

    private final SkinDeformer deformer;
    private float[] geometry;
    @Nullable
    private float[] tangents;
    private long revision;
    private float posedAt = Float.NaN;
    private volatile long touched = System.nanoTime();

    private AnimatedPose(SkinnedModel model) {
        deformer = new SkinDeformer(model.skeleton());
        geometry = new float[model.mesh().vertexCount() * PhotonMesh.FLOATS_PER_GEOMETRY];
    }

    @Nullable
    static AnimatedPose of(SkinnedModel model, @Nullable AnimationClip clip, float seconds,
                           float speed, boolean loop) {
        if (!model.isAnimated()) return null;
        if (POSES.size() > CROWDED) {
            long cutoff = System.nanoTime() - STALE_NANOS;
            POSES.values().removeIf(pose -> pose.touched < cutoff);
        }
        var pose = POSES.computeIfAbsent(new Key(model, clip, speed, loop), key -> new AnimatedPose(model));
        pose.touched = System.nanoTime();
        pose.ensurePosed(model, clip, seconds);
        return pose;
    }

    private synchronized void ensurePosed(SkinnedModel model, @Nullable AnimationClip clip, float seconds) {
        if (posedAt == seconds) return;
        var skin = model.skin();
        if (skin == null) return;
        int vertexCount = model.mesh().vertexCount();
        int geometryFloats = vertexCount * PhotonMesh.FLOATS_PER_GEOMETRY;
        if (geometry.length != geometryFloats) geometry = new float[geometryFloats];
        float[] restTangents = null;
        if (tangents != null) {
            int tangentFloats = vertexCount * PhotonMesh.FLOATS_PER_TANGENT;
            if (tangents.length != tangentFloats) tangents = new float[tangentFloats];
            restTangents = model.mesh().tangents();
        }
        deformer.pose(clip, seconds);
        deformer.deform(model.mesh(), skin, geometry, restTangents, tangents);
        posedAt = seconds;
        if (revision != Long.MAX_VALUE) revision++;
    }

    synchronized float[] geometry() {
        return geometry;
    }

    synchronized float[] tangents(SkinnedModel model, @Nullable AnimationClip clip) {
        if (tangents == null) {
            tangents = new float[model.mesh().vertexCount() * PhotonMesh.FLOATS_PER_TANGENT];
            float lastPose = posedAt;
            if (!Float.isNaN(lastPose)) {
                posedAt = Float.NaN; // current pose did not include tangents; recompute both together
                ensurePosed(model, clip, lastPose);
            }
        }
        return tangents;
    }

    synchronized long revision() {
        return revision;
    }

    static void clear() {
        POSES.clear();
    }
}
