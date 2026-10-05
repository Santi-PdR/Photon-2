package com.lowdragmc.photon.client.gameobject.emitter.data.model.skin;


/**
 * glTF's {@code JOINTS_0} / {@code WEIGHTS_0}, indexed by the mesh's vertex numbering.
 * A vertex whose weights sum to zero is rigid and left alone.
 */
public final class MeshSkin {

    public static final int INFLUENCES = 4;

    private final int[] joints;
    private final float[] weights;
    private final boolean empty;

    public MeshSkin(int[] joints, float[] weights) {
        if (joints.length != weights.length || joints.length % INFLUENCES != 0) {
            throw new IllegalArgumentException("Joint indices and weights must contain four values per vertex");
        }
        this.joints = joints;
        this.weights = weights;
        boolean anyWeight = false;
        for (float weight : weights) anyWeight |= weight != 0f;
        this.empty = !anyWeight;
    }

    public int vertexCount() {
        return joints.length / INFLUENCES;
    }

    public int[] joints() {
        return joints;
    }

    public float[] weights() {
        return weights;
    }

    public boolean isEmpty() {
        return empty;
    }
}
