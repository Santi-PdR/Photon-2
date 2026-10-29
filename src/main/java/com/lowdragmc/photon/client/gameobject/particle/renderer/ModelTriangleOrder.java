/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.ints.IntArrays
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 *  org.joml.Quaternionf
 *  org.joml.Vector3f
 *  org.joml.Vector3fc
 */
package com.lowdragmc.photon.client.gameobject.particle.renderer;

import com.lowdragmc.photon.client.gameobject.particle.renderer.ProjectedTriangleOrder;
import it.unimi.dsi.fastutil.ints.IntArrays;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class ModelTriangleOrder {
    ModelTriangleOrder() {
    }

    static Plan build(float[] centers, int[] triangles, FloatBuffer instances, int stride, int count, Vector3f pivot, Matrix4fc modelView) {
        return ModelTriangleOrder.build(centers, triangles, null, instances, stride, count, pivot, modelView, null);
    }

    static Plan build(float[] centers, int[] triangles, float[] vertices, FloatBuffer instances, int stride, int count, Vector3f pivot, Matrix4fc modelView, Matrix4fc projection) {
        return new Workspace().build(centers, triangles, vertices, instances, stride, count, pivot, modelView, projection);
    }

    record Plan(int[] indices, List<Run> runs, int crossingPairs, int cycleBreaks) {
    }

    static final class Workspace {
        private final ProjectedTriangleOrder.Workspace projected = new ProjectedTriangleOrder.Workspace();
        private int[] order = new int[0];
        private int[] indices = new int[0];
        private float[] depth = new float[0];
        private float[] transforms = new float[0];
        private final Vector3f point = new Vector3f();
        private final Vector3f lastPivot = new Vector3f();
        private final Quaternionf rotation = new Quaternionf();
        private final Matrix4f lastView = new Matrix4f();
        private final Matrix4f lastProjection = new Matrix4f();
        private float[] lastCenters;
        private float[] lastVertices;
        private int[] lastTriangles;
        private int lastCount = -1;
        private boolean hadProjection;
        private Plan plan;
        private final ArrayList<Run> runs = new ArrayList();
        private final List<Run> readOnlyRuns = Collections.unmodifiableList(this.runs);
        private long indexRevision;

        Workspace() {
        }

        long indexRevision() {
            return this.indexRevision;
        }

        private boolean unchanged(float[] centers, int[] triangles, float[] vertices, FloatBuffer instances, int stride, int count, Vector3f pivot, Matrix4fc view, Matrix4fc projection) {
            if (this.plan == null || centers != this.lastCenters || triangles != this.lastTriangles || vertices != this.lastVertices || count != this.lastCount || !this.lastPivot.equals((Object)pivot) || !this.lastView.equals((Object)view) || projection != null != this.hadProjection || projection != null && !this.lastProjection.equals((Object)projection)) {
                return false;
            }
            for (int i = 0; i < count; ++i) {
                for (int c = 0; c < 10; ++c) {
                    if (Float.floatToRawIntBits(this.transforms[i * 10 + c]) == Float.floatToRawIntBits(instances.get(i * stride + c))) continue;
                    return false;
                }
            }
            return true;
        }

        private void run(int slot, int instance, int start, int length) {
            if (slot == this.runs.size()) {
                this.runs.add(new Run(instance, start, length));
            } else {
                Run old = this.runs.get(slot);
                if (old.instance != instance || old.firstIndex != start || old.indexCount != length) {
                    this.runs.set(slot, new Run(instance, start, length));
                }
            }
        }

        Plan build(float[] centers, int[] triangles, float[] vertices, FloatBuffer instances, int stride, int count, Vector3f pivot, Matrix4fc modelView, Matrix4fc projection) {
            int i;
            int indexCount;
            boolean indicesChanged;
            if (this.unchanged(centers, triangles, vertices, instances, stride, count, pivot, modelView, projection)) {
                return this.plan;
            }
            int perInstance = centers.length / 3;
            int total = Math.multiplyExact(perInstance, count);
            if (this.order.length < total) {
                int capacity = Math.max(total, this.order.length + (this.order.length >> 1));
                this.order = new int[capacity];
                this.depth = new float[capacity];
            }
            for (int instance = 0; instance < count; ++instance) {
                int base = instance * stride;
                this.rotation.set(instances.get(base + 6), instances.get(base + 7), instances.get(base + 8), instances.get(base + 9));
                for (int t = 0; t < perInstance; ++t) {
                    int id = instance * perInstance + t;
                    this.point.set(centers[t * 3], centers[t * 3 + 1], centers[t * 3 + 2]).add((Vector3fc)pivot);
                    this.point.mul(instances.get(base + 3), instances.get(base + 4), instances.get(base + 5));
                    this.rotation.transform(this.point);
                    this.point.add(instances.get(base), instances.get(base + 1), instances.get(base + 2));
                    this.point.mulPosition(modelView);
                    this.order[id] = id;
                    this.depth[id] = Float.isFinite(this.point.z) ? this.point.z : 0.0f;
                }
            }
            IntArrays.quickSort((int[])this.order, (int)0, (int)total, (a, b) -> {
                int comparison = Float.compare(this.depth[a], this.depth[b]);
                return comparison != 0 ? comparison : Integer.compare(a, b);
            });
            int[] sorted = this.order;
            int crossingPairs = 0;
            int cycleBreaks = 0;
            if (vertices != null && projection != null) {
                ProjectedTriangleOrder.Result refined = this.projected.refine(vertices, triangles, instances, stride, count, pivot, modelView, projection, this.order);
                sorted = refined.order();
                crossingPairs = refined.crossingPairs();
                cycleBreaks = refined.cycleBreaks();
            }
            boolean bl = indicesChanged = this.indices.length != (indexCount = Math.multiplyExact(total, 3)) || this.plan == null;
            if (this.indices.length != indexCount) {
                this.indices = new int[indexCount];
            }
            int runCount = 0;
            int previousInstance = -1;
            int start = 0;
            for (i = 0; i < total; ++i) {
                int instance = sorted[i] / perInstance;
                if (instance != previousInstance) {
                    if (previousInstance >= 0) {
                        this.run(runCount++, previousInstance, start, i * 3 - start);
                    }
                    previousInstance = instance;
                    start = i * 3;
                }
                int source = sorted[i] % perInstance * 3;
                for (int c = 0; c < 3; ++c) {
                    int target = i * 3 + c;
                    int value = triangles[source + c];
                    indicesChanged |= this.indices[target] != value;
                    this.indices[target] = value;
                }
            }
            if (previousInstance >= 0) {
                this.run(runCount++, previousInstance, start, total * 3 - start);
            }
            while (this.runs.size() > runCount) {
                this.runs.remove(this.runs.size() - 1);
            }
            if (indicesChanged) {
                ++this.indexRevision;
            }
            if (this.transforms.length < count * 10) {
                this.transforms = new float[count * 10];
            }
            for (i = 0; i < count; ++i) {
                for (int c = 0; c < 10; ++c) {
                    this.transforms[i * 10 + c] = instances.get(i * stride + c);
                }
            }
            this.lastCenters = centers;
            this.lastTriangles = triangles;
            this.lastVertices = vertices;
            this.lastCount = count;
            this.lastPivot.set((Vector3fc)pivot);
            this.lastView.set(modelView);
            boolean bl2 = this.hadProjection = projection != null;
            if (projection != null) {
                this.lastProjection.set(projection);
            }
            this.plan = new Plan(this.indices, this.readOnlyRuns, crossingPairs, cycleBreaks);
            return this.plan;
        }
    }

    record Run(int instance, int firstIndex, int indexCount) {
    }
}

