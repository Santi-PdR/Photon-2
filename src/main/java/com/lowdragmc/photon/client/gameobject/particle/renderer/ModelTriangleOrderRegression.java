package com.lowdragmc.photon.client.gameobject.particle.renderer;

import java.nio.FloatBuffer;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Called only by the opt-in remote editor smoke test. */
public final class ModelTriangleOrderRegression {
    private ModelTriangleOrderRegression() {}

    public static void verify() {
        float[] centers = {0, 0, -1, 0, 0, -3};
        int[] triangles = {0, 1, 2, 3, 4, 5};
        FloatBuffer instances = FloatBuffer.wrap(new float[]{
                0, 0, 0, 1, 1, 1, 0, 0, 0, 1,
                0, 0, -1, 1, 1, 1, 0, 0, 0, 1});
        var workspace = new ModelTriangleOrder.Workspace();
        var order = workspace.build(centers, triangles, null, instances, 10, 2, new Vector3f(), new Matrix4f(), null);
        if (!Arrays.equals(order.indices(), new int[]{3, 4, 5, 3, 4, 5, 0, 1, 2, 0, 1, 2}))
            throw new IllegalStateException("Incorrect far-to-near triangle order");
        if (order.runs().size() != 4 || order.runs().get(0).instance() != 1 || order.runs().get(1).instance() != 0)
            throw new IllegalStateException("Incorrect interleaved instance runs");
        long revision = workspace.indexRevision();
        workspace.build(centers, triangles, null, instances, 10, 2, new Vector3f(), new Matrix4f(), null);
        if (workspace.indexRevision() != revision) throw new IllegalStateException("Unchanged sort was rebuilt");
        instances.put(12, 2);
        var changed = workspace.build(centers, triangles, null, instances, 10, 2, new Vector3f(), new Matrix4f(), null);
        if (workspace.indexRevision() == revision || changed.runs().get(0).instance() != 0)
            throw new IllegalStateException("Moving an instance did not reorder triangles");
        float[] vertices = {-0.3f, -0.3f, -1, 0.3f, -0.3f, -1, 0, 0.3f, -1,
                -0.9f, -0.9f, -3, 0.9f, -0.9f, -3, 0, 0.9f, -3};
        var projected = new ModelTriangleOrder.Workspace().build(centers, triangles, vertices, instances, 10, 1,
                new Vector3f(), new Matrix4f(), new Matrix4f().perspective((float) Math.toRadians(90), 1, 0.1f, 100));
        if (!Arrays.equals(projected.indices(), new int[]{3, 4, 5, 0, 1, 2}))
            throw new IllegalStateException("Incorrect projected overlapping triangle order");
    }
}
