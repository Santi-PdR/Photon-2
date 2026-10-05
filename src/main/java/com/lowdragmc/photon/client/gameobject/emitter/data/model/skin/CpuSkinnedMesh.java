package com.lowdragmc.photon.client.gameobject.emitter.data.model.skin;

import com.lowdragmc.photon.client.gameobject.emitter.data.model.IDynamicMesh;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import java.util.List;

/**
 * CPU skinning adapter for the revision-aware Forge renderer. Call {@link #pose(AnimationClip, float)}
 * when the animation clock advances, then expose this through {@code DynamicMeshSource}.
 */
public final class CpuSkinnedMesh implements IDynamicMesh {
    private final PhotonMesh topology;
    private final MeshSkin skin;
    private final SkinDeformer deformer;
    private final float[] geometry;
    private final float[] tangents;
    private long revision;
    private AnimationClip lastClip;
    private int lastTimeBits;

    public CpuSkinnedMesh(PhotonMesh topology, MeshSkin skin, Skeleton skeleton) {
        this.topology = topology;
        this.skin = skin;
        this.deformer = new SkinDeformer(skeleton);
        int vertexCount = topology.quadCount() * 4;
        if (skin.vertexCount() != vertexCount) {
            throw new IllegalArgumentException("Skin vertex count must match the mesh's quad-corner count");
        }
        this.geometry = new float[vertexCount * 6];
        this.tangents = new float[vertexCount * PhotonMesh.FLOATS_PER_TANGENT];
    }

    @Override public PhotonMesh topology() { return topology; }
    @Override public synchronized long revision() { return revision; }
    @Override public synchronized float[] geometry() { return revision == 0 ? null : geometry; }
    @Override public synchronized float[] tangents() { return revision == 0 ? null : tangents; }

    public synchronized void pose(AnimationClip clip, float timeSeconds) {
        int timeBits = Float.floatToIntBits(timeSeconds);
        if (revision != 0 && clip == lastClip && timeBits == lastTimeBits) return;
        deformer.pose(clip, timeSeconds);
        deformer.deform(topology, skin, geometry, tangents);
        lastClip = clip;
        lastTimeBits = timeBits;
        if (revision != Long.MAX_VALUE) revision++;
    }

    public SkinnedModel model(List<AnimationClip> clips) {
        return new SkinnedModel(topology, skin, deformer.skeleton(), List.copyOf(clips));
    }
}
