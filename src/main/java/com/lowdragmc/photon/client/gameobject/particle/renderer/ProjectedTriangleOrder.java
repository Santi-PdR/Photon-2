/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.ints.IntArrays
 *  it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue
 *  org.joml.Matrix4fc
 *  org.joml.Quaternionf
 *  org.joml.Vector3f
 *  org.joml.Vector3fc
 *  org.joml.Vector4f
 */
package com.lowdragmc.photon.client.gameobject.particle.renderer;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

final class ProjectedTriangleOrder {
    ProjectedTriangleOrder() {
    }

    static int relation(Triangle a, Triangle b, double[] scratchA, double[] scratchB) {
        if (a == null || b == null || a.maxX <= b.minX || b.maxX <= a.minX || a.maxY <= b.minY || b.maxY <= a.minY) {
            return 0;
        }
        if (a.maxSum < b.minSum || b.maxSum < a.minSum || a.maxDifference < b.minDifference || b.maxDifference < a.minDifference) {
            return 0;
        }
        int count = 3;
        double[] input = a.xy;
        double[] output = scratchA;
        for (int edge = 0; edge < 3; ++edge) {
            double x = b.xy[edge * 2];
            double y = b.xy[edge * 2 + 1];
            double dx = b.edges[edge * 2];
            double dy = b.edges[edge * 2 + 1];
            int written = 0;
            double px = input[(count - 1) * 2];
            double py = input[(count - 1) * 2 + 1];
            double previous = dx * (py - y) - dy * (px - x);
            for (int i = 0; i < count; ++i) {
                double cy = input[i * 2 + 1];
                double cx = input[i * 2];
                double current = dx * (cy - y) - dy * (cx - x);
                if (current >= 0.0 != previous >= 0.0) {
                    double f = previous / (previous - current);
                    output[written * 2] = px + (cx - px) * f;
                    output[written * 2 + 1] = py + (cy - py) * f;
                    ++written;
                }
                if (current >= 0.0) {
                    output[written * 2] = cx;
                    output[written * 2 + 1] = cy;
                    ++written;
                }
                px = cx;
                py = cy;
                previous = current;
            }
            if (written < 3) {
                return 0;
            }
            count = written;
            input = output;
            output = input == scratchA ? scratchB : scratchA;
        }
        double area = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; ++i) {
            int next = i + 1 == count ? 0 : i + 1;
            area += input[i * 2] * input[next * 2 + 1] - input[next * 2] * input[i * 2 + 1];
            double difference = (a.zx - b.zx) * input[i * 2] + (a.zy - b.zy) * input[i * 2 + 1] + a.z0 - b.z0;
            min = Math.min(min, difference);
            max = Math.max(max, difference);
        }
        if (Math.abs(area) < 1.0E-14) {
            return 0;
        }
        double epsilon = 1.0E-9;
        if (min < -epsilon && max > epsilon) {
            return 2;
        }
        if (max > epsilon) {
            return 1;
        }
        if (min < -epsilon) {
            return -1;
        }
        return 0;
    }

    static Result refine(Triangle[] triangles, int[] fallback) {
        return new Workspace().refine(triangles, fallback, triangles.length);
    }

    static Result refine(float[] vertices, int[] indices, FloatBuffer instances, int stride, int count, Vector3f pivot, Matrix4fc view, Matrix4fc projection, int[] fallback) {
        return new Workspace().refine(vertices, indices, instances, stride, count, pivot, view, projection, fallback);
    }

    static final class Triangle {
        final double[] xy = new double[6];
        final double[] edges = new double[6];
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minSum;
        double maxSum;
        double minDifference;
        double maxDifference;
        double zx;
        double zy;
        double z0;

        Triangle() {
        }

        static Triangle of(double ... xyz) {
            return new Triangle().set(xyz);
        }

        private Triangle set(double[] xyz) {
            Triangle t = this;
            this.minY = Double.POSITIVE_INFINITY;
            this.minX = Double.POSITIVE_INFINITY;
            this.maxY = Double.NEGATIVE_INFINITY;
            this.maxX = Double.NEGATIVE_INFINITY;
            this.minDifference = Double.POSITIVE_INFINITY;
            this.minSum = Double.POSITIVE_INFINITY;
            this.maxDifference = Double.NEGATIVE_INFINITY;
            this.maxSum = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 3; ++i) {
                double x = xyz[i * 3];
                double y = xyz[i * 3 + 1];
                if (!(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(xyz[i * 3 + 2]))) {
                    return null;
                }
                t.xy[i * 2] = x;
                t.xy[i * 2 + 1] = y;
                t.minX = Math.min(t.minX, x);
                t.maxX = Math.max(t.maxX, x);
                t.minY = Math.min(t.minY, y);
                t.maxY = Math.max(t.maxY, y);
                this.minSum = Math.min(this.minSum, x + y);
                this.maxSum = Math.max(this.maxSum, x + y);
                this.minDifference = Math.min(this.minDifference, x - y);
                this.maxDifference = Math.max(this.maxDifference, x - y);
            }
            this.minSum = Math.nextDown(this.minSum);
            this.maxSum = Math.nextUp(this.maxSum);
            this.minDifference = Math.nextDown(this.minDifference);
            this.maxDifference = Math.nextUp(this.maxDifference);
            double dx1 = xyz[3] - xyz[0];
            double dy1 = xyz[4] - xyz[1];
            double dz1 = xyz[5] - xyz[2];
            double dx2 = xyz[6] - xyz[0];
            double dy2 = xyz[7] - xyz[1];
            double dz2 = xyz[8] - xyz[2];
            double determinant = dx1 * dy2 - dx2 * dy1;
            if (Math.abs(determinant) < 1.0E-14) {
                return null;
            }
            t.zx = (dz1 * dy2 - dz2 * dy1) / determinant;
            t.zy = (dx1 * dz2 - dx2 * dz1) / determinant;
            t.z0 = xyz[2] - t.zx * xyz[0] - t.zy * xyz[1];
            if (determinant < 0.0) {
                double x = t.xy[2];
                double y = t.xy[3];
                t.xy[2] = t.xy[4];
                t.xy[3] = t.xy[5];
                t.xy[4] = x;
                t.xy[5] = y;
            }
            for (int edge = 0; edge < 3; ++edge) {
                int next = (edge + 1) % 3;
                this.edges[edge * 2] = this.xy[next * 2] - this.xy[edge * 2];
                this.edges[edge * 2 + 1] = this.xy[next * 2 + 1] - this.xy[edge * 2 + 1];
            }
            return t;
        }
    }

    static final class Workspace {
        private int[] sweep = new int[0];
        private int[] rank = new int[0];
        private int[] indegree = new int[0];
        private int[] head = new int[0];
        private int[] edgeTo = new int[0];
        private int[] edgeNext = new int[0];
        private int[] result = new int[0];
        private boolean[] emitted = new boolean[0];
        private final double[] scratchA = new double[20];
        private final double[] scratchB = new double[20];
        private final double[] corners = new double[9];
        private final IntHeapPriorityQueue ready = new IntHeapPriorityQueue((a, b) -> Integer.compare(this.rank[a], this.rank[b]));
        private Triangle[] projected = new Triangle[0];
        private Triangle[] pool = new Triangle[0];
        private final Quaternionf q = new Quaternionf();
        private final Vector3f p = new Vector3f();
        private final Vector4f clip = new Vector4f();
        private float[] sourceVertices;
        private float[] uniqueVertices = new float[0];
        private int[] sourceIndices;
        private int[] uniqueIndices = new int[0];
        private double[] positions = new double[0];
        private boolean[] usable = new boolean[0];

        Workspace() {
        }

        private void ensureCapacity(int n) {
            if (this.sweep.length < n) {
                int capacity = Math.max(n, this.sweep.length + (this.sweep.length >> 1));
                this.sweep = new int[capacity];
                this.rank = new int[capacity];
                this.indegree = new int[capacity];
                this.head = new int[capacity];
                this.emitted = new boolean[capacity];
                this.projected = new Triangle[capacity];
                this.pool = Arrays.copyOf(this.pool, capacity);
            }
            if (this.result.length != n) {
                this.result = new int[n];
            }
        }

        Result refine(Triangle[] triangles, int[] fallback, int n) {
            int a2;
            int i;
            this.ensureCapacity(n);
            Arrays.fill(this.indegree, 0, n, 0);
            Arrays.fill(this.head, 0, n, -1);
            Arrays.fill(this.emitted, 0, n, false);
            this.ready.clear();
            for (int i2 = 0; i2 < n; ++i2) {
                this.sweep[i2] = i2;
                this.rank[fallback[i2]] = i2;
            }
            IntArrays.quickSort((int[])this.sweep, (int)0, (int)n, (a, b) -> Double.compare(triangles[a] == null ? Double.POSITIVE_INFINITY : triangles[a].minX, triangles[b] == null ? Double.POSITIVE_INFINITY : triangles[b].minX));
            int crossings = 0;
            int edges = 0;
            for (i = 0; i < n && triangles[a2 = this.sweep[i]] != null; ++i) {
                int b2;
                for (int j = i + 1; j < n && triangles[b2 = this.sweep[j]] != null && !(triangles[b2].minX >= triangles[a2].maxX); ++j) {
                    int near;
                    int relation = ProjectedTriangleOrder.relation(triangles[a2], triangles[b2], this.scratchA, this.scratchB);
                    if (relation == 0) continue;
                    if (relation == 2) {
                        ++crossings;
                        continue;
                    }
                    int far = relation == 1 ? a2 : b2;
                    int n2 = near = relation == 1 ? b2 : a2;
                    if (edges == this.edgeTo.length) {
                        int capacity = Math.max(64, edges + (edges >> 1));
                        this.edgeTo = Arrays.copyOf(this.edgeTo, capacity);
                        this.edgeNext = Arrays.copyOf(this.edgeNext, capacity);
                    }
                    this.edgeTo[edges] = near;
                    this.edgeNext[edges] = this.head[far];
                    this.head[far] = edges++;
                    int n3 = near;
                    this.indegree[n3] = this.indegree[n3] + 1;
                }
            }
            for (i = 0; i < n; ++i) {
                if (this.indegree[i] != 0) continue;
                this.ready.enqueue(i);
            }
            int cycles = 0;
            int cursor = 0;
            for (int i3 = 0; i3 < n; ++i3) {
                if (this.ready.isEmpty()) {
                    while (this.emitted[fallback[cursor]]) {
                        ++cursor;
                    }
                    this.ready.enqueue(fallback[cursor]);
                    ++cycles;
                }
                int id = this.ready.dequeueInt();
                this.emitted[id] = true;
                this.result[i3] = id;
                int edge = this.head[id];
                while (edge != -1) {
                    int next = this.edgeTo[edge];
                    if (!this.emitted[next]) {
                        int n4 = next;
                        this.indegree[n4] = this.indegree[n4] - 1;
                        if (this.indegree[n4] == 0) {
                            this.ready.enqueue(next);
                        }
                    }
                    edge = this.edgeNext[edge];
                }
            }
            return new Result(this.result, crossings, cycles);
        }

        private void geometry(float[] vertices, int[] indices) {
            if (vertices == this.sourceVertices && indices == this.sourceIndices) {
                return;
            }
            this.sourceVertices = vertices;
            this.sourceIndices = indices;
            HashMap<Position, Integer> ids = new HashMap<Position, Integer>();
            float[] unique = new float[vertices.length];
            this.uniqueIndices = new int[indices.length];
            for (int i = 0; i < indices.length; ++i) {
                int offset = indices[i] * 3;
                Position key = new Position(Float.floatToRawIntBits(vertices[offset]), Float.floatToRawIntBits(vertices[offset + 1]), Float.floatToRawIntBits(vertices[offset + 2]));
                Integer id = (Integer)ids.get(key);
                if (id == null) {
                    id = ids.size();
                    ids.put(key, id);
                    System.arraycopy(vertices, offset, unique, id * 3, 3);
                }
                this.uniqueIndices[i] = id;
            }
            this.uniqueVertices = Arrays.copyOf(unique, ids.size() * 3);
            this.positions = new double[this.uniqueVertices.length];
            this.usable = new boolean[ids.size()];
        }

        Result refine(float[] vertices, int[] indices, FloatBuffer instances, int stride, int count, Vector3f pivot, Matrix4fc view, Matrix4fc projection, int[] fallback) {
            int perInstance = indices.length / 3;
            int total = Math.multiplyExact(perInstance, count);
            this.ensureCapacity(total);
            this.geometry(vertices, indices);
            for (int instance = 0; instance < count; ++instance) {
                int base = instance * stride;
                this.q.set(instances.get(base + 6), instances.get(base + 7), instances.get(base + 8), instances.get(base + 9));
                for (int vertex = 0; vertex < this.usable.length; ++vertex) {
                    int index = vertex * 3;
                    this.p.set(this.uniqueVertices[index], this.uniqueVertices[index + 1], this.uniqueVertices[index + 2]).add((Vector3fc)pivot).mul(instances.get(base + 3), instances.get(base + 4), instances.get(base + 5));
                    this.q.transform(this.p);
                    this.p.add(instances.get(base), instances.get(base + 1), instances.get(base + 2)).mulPosition(view);
                    this.clip.set((Vector3fc)this.p, 1.0f).mul(projection);
                    this.usable[vertex] = !(this.clip.w <= 0.0f) && !(this.clip.z < -this.clip.w);
                    this.positions[index] = (double)this.clip.x / (double)this.clip.w;
                    this.positions[index + 1] = (double)this.clip.y / (double)this.clip.w;
                    this.positions[index + 2] = (double)this.clip.z / (double)this.clip.w;
                }
                for (int t = 0; t < perInstance; ++t) {
                    boolean valid = true;
                    for (int corner = 0; corner < 3; ++corner) {
                        int vertex = this.uniqueIndices[t * 3 + corner];
                        if (!this.usable[vertex]) {
                            valid = false;
                            break;
                        }
                        System.arraycopy(this.positions, vertex * 3, this.corners, corner * 3, 3);
                    }
                    int id = instance * perInstance + t;
                    if (valid) {
                        if (this.pool[id] == null) {
                            this.pool[id] = new Triangle();
                        }
                        this.projected[id] = this.pool[id].set(this.corners);
                        continue;
                    }
                    this.projected[id] = null;
                }
            }
            return this.refine(this.projected, fallback, total);
        }

        private record Position(int x, int y, int z) {
        }
    }

    record Result(int[] order, int crossingPairs, int cycleBreaks) {
    }
}

