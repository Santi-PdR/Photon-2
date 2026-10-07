package com.lowdragmc.photon.client.gameobject.emitter.data.model.skin;


/**
 * glTF's joint/weight attribute sets, indexed by the mesh's vertex numbering.
 * A vertex whose weights sum to zero is rigid and left alone.
 */
public final class MeshSkin {

    public static final int INFLUENCES = 4;
    public static final int MAX_INFLUENCES = 8;

    private final int[] joints;
    private final float[] weights;
    private final int influences;
    private final boolean empty;

    public MeshSkin(int[] joints, float[] weights) {
        this(joints, weights, INFLUENCES);
    }

    public MeshSkin(int[] joints, float[] weights, int influences) {
        if (influences < 1 || influences > MAX_INFLUENCES
                || joints.length != weights.length || joints.length % influences != 0) {
            throw new IllegalArgumentException("Joint indices and weights must contain one to eight values per vertex");
        }
        this.joints = joints;
        this.weights = weights;
        this.influences = influences;
        boolean anyWeight = false;
        for (float weight : weights) anyWeight |= weight != 0f;
        this.empty = !anyWeight;
    }

    public int vertexCount() {
        return joints.length / influences;
    }

    public int influences() {
        return influences;
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
