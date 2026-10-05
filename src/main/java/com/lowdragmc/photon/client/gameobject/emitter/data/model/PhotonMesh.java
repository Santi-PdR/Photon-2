package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.IQuadTransformer;
import org.apache.commons.lang3.tuple.Pair;
import javax.annotation.Nullable;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Immutable geometry shared by every {@link IModelSource}: flat quads of pos(3)+uv(2)+normal(3), plus a
 * parallel tangent(3)+handedness(1) array — the source's own when it has one (glTF's {@code TANGENT}),
 * otherwise derived from the UVs on demand (see {@link MeshTangents}).
 * Positions are in <b>centered model space</b> — a JSON block model's 0..1 cube is stored as
 * -0.5..0.5, OBJ positions are the raw author space (origin = pivot). Triangles are stored as
 * degenerate quads (corner 3 == corner 2, zero-area second half) so the QUADS-mode render paths
 * and the 6-indices-per-quad EBO layout stay untouched. Consumers compare instances by identity
 * to detect cache invalidation ({@link PhotonMeshCache} hands out a new instance after reload).
 */
@OnlyIn(Dist.CLIENT)
public final class PhotonMesh {
    public static final int FLOATS_PER_VERTEX = 8; // pos3 + uv2 + normal3
    public static final int FLOATS_PER_GEOMETRY = 6; // deformed pos3 + normal3 per mesh vertex
    /** Floats per corner in {@link #tangents()}: tangent xyz + handedness. */
    public static final int FLOATS_PER_TANGENT = MeshTangents.FLOATS_PER_TANGENT;
    public static final PhotonMesh EMPTY = new PhotonMesh(new float[0], new float[0], new float[0], new float[0]);

    /** quadCount * 4 * {@link #FLOATS_PER_VERTEX}: x,y,z,u,v,nx,ny,nz per corner. */
    private final float[] vertices;
    /** quadCount * 4: u0,v0,u1,v1 sprite bounds per quad ({@code 0,0,1,1} when UVs are already raw). */
    private final float[] spriteBounds;
    /** quadCount: per-face directional shade factor (all 1 when the source has no face directions). */
    private final float[] shadeBrightness;
    /**
     * quadCount * 4 * {@link #FLOATS_PER_TANGENT}: tx,ty,tz,w per corner, derived from positions + UVs
     * ({@link MeshTangents}). Kept in a parallel array rather than widening {@link #vertices} so the
     * {@code vertexOffset + component} indexing every existing consumer uses stays put. Built on first
     * {@link #tangents()} unless the source supplied its own — only the model render path asks, and only
     * when the emitter's Tangent setting is on, so a mesh used purely for emission shapes or by a
     * tangent-free emitter never pays for it.
     */
    @Nullable
    private volatile float[] tangents;
    @Nullable
    private Supplier<float[]> tangentSupplier;
    /** Revision used by runtime-only dynamic mesh sources; static meshes are revision zero. */
    private final long geometryRevision;
    /** Stable base identity and source topology, retained across animated geometry snapshots. */
    private final PhotonMesh topology;
    private final SamplingTopology samplingTopology;

    private PhotonMesh(float[] vertices, float[] spriteBounds, float[] shadeBrightness,
                       @Nullable float[] suppliedTangents) {
        this(vertices, spriteBounds, shadeBrightness, suppliedTangents, 0L);
    }

    private PhotonMesh(float[] vertices, float[] spriteBounds, float[] shadeBrightness,
                       @Nullable float[] suppliedTangents, long geometryRevision) {
        this.vertices = vertices;
        this.spriteBounds = spriteBounds;
        this.shadeBrightness = shadeBrightness;
        // A source that carries real tangents (glTF's TANGENT attribute) seeds the cache, so tangents()
        // hands those back and never generates. Null = nothing supplied them; generate on demand.
        this.tangents = suppliedTangents;
        this.geometryRevision = geometryRevision;
        this.topology = this;
        this.samplingTopology = SamplingTopology.unwelded(shadeBrightness.length);
    }

    private PhotonMesh(float[] vertices, float[] spriteBounds, float[] shadeBrightness,
                       @Nullable float[] suppliedTangents, SamplingTopology samplingTopology) {
        this.vertices = vertices;
        this.spriteBounds = spriteBounds;
        this.shadeBrightness = shadeBrightness;
        this.tangents = suppliedTangents;
        this.geometryRevision = 0L;
        this.topology = this;
        this.samplingTopology = samplingTopology;
    }

    private PhotonMesh(PhotonMesh base, float[] vertices, long geometryRevision) {
        this.vertices = vertices;
        this.spriteBounds = base.spriteBounds;
        this.shadeBrightness = base.shadeBrightness;
        this.geometryRevision = geometryRevision;
        this.topology = base.topology;
        this.samplingTopology = base.samplingTopology;
        this.tangents = null;
    }

    public int quadCount() {
        return shadeBrightness.length;
    }

    public int vertexCount() {
        return quadCount() * 4;
    }

    public boolean isEmpty() {
        return shadeBrightness.length == 0;
    }

    /** Revision of runtime-deformed geometry, or zero for ordinary immutable meshes. */
    public long geometryRevision() {
        return geometryRevision;
    }

    /** Stable source-topology identity shared by every animated pose. */
    public PhotonMesh topology() {
        return topology;
    }

    public SamplingTopology samplingTopology() {
        return samplingTopology;
    }

    /** Source vertices and triangles used by mesh emission. */
    public static final class SamplingTopology {
        private final int[] firstCorner;
        private final int[] triangles;

        private SamplingTopology(int[] firstCorner, int[] triangles) {
            this.firstCorner = firstCorner;
            this.triangles = triangles;
        }

        private static SamplingTopology unwelded(int quads) {
            var first = new int[quads * 4];
            var triangles = new int[quads * 6];
            for (int q = 0; q < quads; q++) {
                int base = q * 4, t = q * 6;
                first[base] = base;
                first[base + 1] = base + 1;
                first[base + 2] = base + 2;
                first[base + 3] = base + 3;
                triangles[t] = base; triangles[t + 1] = base + 1; triangles[t + 2] = base + 2;
                triangles[t + 3] = base + 2; triangles[t + 4] = base + 3; triangles[t + 5] = base;
            }
            return new SamplingTopology(first, triangles);
        }

        public int vertexCount() { return firstCorner.length; }
        public int triangleCount() { return triangles.length / 3; }
        public int firstCorner(int vertex) { return firstCorner[vertex]; }
        public int triangleVertex(int triangle, int corner) { return triangles[triangle * 3 + corner]; }
    }

    /**
     * Create a mesh with new per-corner positions and normals while retaining this mesh's UVs,
     * sprite bounds, face shade, and quad order. Geometry uses six floats per vertex: xyz + normal.
     */
    public PhotonMesh withGeometry(float[] geometry, @Nullable float[] deformedTangents, long revision) {
        return withGeometry(geometry, deformedTangents == null ? null : () -> deformedTangents, revision);
    }

    /** Dynamic variant that defers tangent deformation until a material actually needs tangents. */
    public PhotonMesh withGeometry(float[] geometry, @Nullable Supplier<float[]> deformedTangents, long revision) {
        if (geometry.length != vertexCount() * FLOATS_PER_GEOMETRY) {
            throw new IllegalArgumentException("Dynamic geometry must contain six floats per mesh vertex");
        }
        var updated = vertices.clone();
        for (int vertex = 0; vertex < vertexCount(); vertex++) {
            int source = vertex * FLOATS_PER_GEOMETRY;
            int target = vertex * FLOATS_PER_VERTEX;
            System.arraycopy(geometry, source, updated, target, 3);
            System.arraycopy(geometry, source + 3, updated, target + 5, 3);
        }
        var result = new PhotonMesh(this, updated, revision);
        result.tangentSupplier = deformedTangents;
        return result;
    }

    public float[] vertices() {
        return vertices;
    }

    public float[] spriteBounds() {
        return spriteBounds;
    }

    /**
     * Per-corner {@code tx,ty,tz,w}; the shader rebuilds the bitangent as {@code cross(N, T) * w}.
     * The source's own tangents when it supplied them (glTF), otherwise generated on first call and
     * memoized. The generation is pure and depends only on final fields, so two threads racing to fill
     * the cache produce identical arrays — a benign race, no lock needed, and the instance stays
     * observably immutable.
     */
    public float[] tangents() {
        var cached = tangents;
        if (cached == null) {
            var supplier = tangentSupplier;
            if (supplier != null) {
                var deformed = supplier.get();
                cached = deformed != null && deformed.length == vertexCount() * FLOATS_PER_TANGENT
                        ? deformed.clone() : MeshTangents.generate(vertices, spriteBounds, shadeBrightness.length);
                tangentSupplier = null;
            } else {
                cached = MeshTangents.generate(vertices, spriteBounds, shadeBrightness.length);
            }
            tangents = cached;
        }
        return cached;
    }

    public float shadeBrightness(int quad) {
        return shadeBrightness[quad];
    }

    /** Offset of {@code corner} (0..3) of {@code quad} into {@link #vertices()}. */
    public static int vertexOffset(int quad, int corner) {
        return (quad * 4 + corner) * FLOATS_PER_VERTEX;
    }

    /** Offset of {@code corner} (0..3) of {@code quad} into {@link #tangents()}. */
    public static int tangentOffset(int quad, int corner) {
        return (quad * 4 + corner) * FLOATS_PER_TANGENT;
    }

    /** True when the quad is a degenerate triangle (corner 3 repeats corner 2). */
    public boolean isTriangle(int quad) {
        int c2 = vertexOffset(quad, 2);
        int c3 = vertexOffset(quad, 3);
        return vertices[c2] == vertices[c3]
                && vertices[c2 + 1] == vertices[c3 + 1]
                && vertices[c2 + 2] == vertices[c3 + 2];
    }

    /**
     * Decode baked quads (with their per-face shade factor) into a mesh. Positions are shifted by
     * -0.5 into centered space; sprite bounds are recorded so {@code useBlockUV=false} can remap
     * atlas UVs back to 0..1 at consumption time.
     */
    public static PhotonMesh fromBakedQuads(List<Pair<BakedQuad, Float>> quads) {
        var builder = new Builder();
        var corners = new float[4][FLOATS_PER_VERTEX];
        for (var pair : quads) {
            var quad = pair.getLeft();
            int[] data = quad.getVertices();
            int points = Math.min(data.length / IQuadTransformer.STRIDE, 4);
            if (points < 3) continue;
            for (int k = 0; k < points; k++) {
                int off = k * IQuadTransformer.STRIDE;
                var corner = corners[k];
                corner[0] = Float.intBitsToFloat(data[off + IQuadTransformer.POSITION]) - 0.5f;
                corner[1] = Float.intBitsToFloat(data[off + IQuadTransformer.POSITION + 1]) - 0.5f;
                corner[2] = Float.intBitsToFloat(data[off + IQuadTransformer.POSITION + 2]) - 0.5f;
                corner[3] = Float.intBitsToFloat(data[off + IQuadTransformer.UV0]);
                corner[4] = Float.intBitsToFloat(data[off + IQuadTransformer.UV0 + 1]);
                int packedNormal = data[off + IQuadTransformer.NORMAL];
                corner[5] = ((byte) packedNormal) / 127.0f;
                corner[6] = ((byte) (packedNormal >> 8)) / 127.0f;
                corner[7] = ((byte) (packedNormal >> 16)) / 127.0f;
            }
            if (points == 3) {
                System.arraycopy(corners[2], 0, corners[3], 0, FLOATS_PER_VERTEX);
            }
            var sprite = quad.getSprite();
            builder.quad(corners[0], corners[1], corners[2], corners[3],
                    sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), pair.getRight());
        }
        return builder.build();
    }

    public static final class Builder {
        private final FloatArrayList vertices = new FloatArrayList();
        private final FloatArrayList spriteBounds = new FloatArrayList();
        private final FloatArrayList shadeBrightness = new FloatArrayList();
        /** Author-supplied per-corner tangents, {@link #FLOATS_PER_TANGENT} each. Only used when EVERY
         *  face supplied one — {@link #build()} checks the count, so a mesh mixing sources (a glTF whose
         *  primitives disagree about TANGENT) falls back to generating the whole array. */
        private final FloatArrayList tangents = new FloatArrayList();
        private final IntArrayList samplingCorners = new IntArrayList();
        private final Map<Object, Integer> samplingIds = new HashMap<>();
        private int nextSamplingGroup;

        /** Allocate a namespace for vertices from one OBJ object or glTF primitive instance. */
        public int newSamplingGroup() { return nextSamplingGroup++; }

        /** Get a stable sampling id for an importer vertex in a namespaced source domain. */
        public int samplingVertex(int group, int sourceIndex) {
            return samplingIds.computeIfAbsent(new GroupVertex(group, sourceIndex), ignored -> samplingIds.size());
        }

        /** Get a stable sampling id for format-specific identities such as an OBJ v/vt/vn tuple. */
        public int samplingVertex(Object sourceIdentity) {
            return samplingIds.computeIfAbsent(sourceIdentity, ignored -> samplingIds.size());
        }

        private record GroupVertex(int group, int sourceIndex) { }

        /** Each corner is {@link #FLOATS_PER_VERTEX} floats: x,y,z,u,v,nx,ny,nz. */
        public Builder quad(float[] a, float[] b, float[] c, float[] d,
                            float u0, float v0, float u1, float v1, float brightness) {
            int group = newSamplingGroup();
            int ia = samplingVertex(group, 0);
            int ib = samplingVertex(group, 1);
            int ic = samplingVertex(group, 2);
            int id = samePosition(c, d) ? ic : samplingVertex(group, 3);
            return quad(a, b, c, d, u0, v0, u1, v1, brightness, ia, ib, ic, id);
        }

        private Builder quad(float[] a, float[] b, float[] c, float[] d,
                             float u0, float v0, float u1, float v1, float brightness,
                             int ia, int ib, int ic, int id) {
            vertices.addElements(vertices.size(), a, 0, FLOATS_PER_VERTEX);
            vertices.addElements(vertices.size(), b, 0, FLOATS_PER_VERTEX);
            vertices.addElements(vertices.size(), c, 0, FLOATS_PER_VERTEX);
            vertices.addElements(vertices.size(), d, 0, FLOATS_PER_VERTEX);
            spriteBounds.add(u0);
            spriteBounds.add(v0);
            spriteBounds.add(u1);
            spriteBounds.add(v1);
            shadeBrightness.add(brightness);
            samplingCorners.add(ia);
            samplingCorners.add(ib);
            samplingCorners.add(ic);
            samplingCorners.add(id);
            return this;
        }

        private static boolean samePosition(float[] a, float[] b) {
            return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
        }

        /** One triangle stored as a degenerate quad (corner 3 == corner 2), raw 0..1 UVs, no shade. */
        public Builder triangle(float[] a, float[] b, float[] c) {
            return quad(a, b, c, c, 0f, 0f, 1f, 1f, 1f);
        }

        public Builder triangle(float[] a, float[] b, float[] c, int ia, int ib, int ic) {
            return quad(a, b, c, c, 0f, 0f, 1f, 1f, 1f, ia, ib, ic, ic);
        }

        /**
         * A triangle whose tangents come from the source itself rather than being derived — glTF's
         * {@code TANGENT} attribute, whose {@code vec4} (unit tangent + handedness) is already Photon's
         * convention. Each {@code t*} is {@link #FLOATS_PER_TANGENT} floats.
         */
        public Builder triangle(float[] a, float[] b, float[] c, float[] ta, float[] tb, float[] tc) {
            quad(a, b, c, c, 0f, 0f, 1f, 1f, 1f);
            tangents.addElements(tangents.size(), ta, 0, FLOATS_PER_TANGENT);
            tangents.addElements(tangents.size(), tb, 0, FLOATS_PER_TANGENT);
            tangents.addElements(tangents.size(), tc, 0, FLOATS_PER_TANGENT);
            // corner 3 repeats corner 2, exactly as the position/uv/normal copy above does
            tangents.addElements(tangents.size(), tc, 0, FLOATS_PER_TANGENT);
            return this;
        }

        public Builder triangle(float[] a, float[] b, float[] c, float[] ta, float[] tb, float[] tc,
                                int ia, int ib, int ic) {
            quad(a, b, c, c, 0f, 0f, 1f, 1f, 1f, ia, ib, ic, ic);
            tangents.addElements(tangents.size(), ta, 0, FLOATS_PER_TANGENT);
            tangents.addElements(tangents.size(), tb, 0, FLOATS_PER_TANGENT);
            tangents.addElements(tangents.size(), tc, 0, FLOATS_PER_TANGENT);
            tangents.addElements(tangents.size(), tc, 0, FLOATS_PER_TANGENT);
            return this;
        }

        public PhotonMesh build() {
            if (shadeBrightness.isEmpty()) {
                return EMPTY;
            }
            var supplied = tangents.size() == shadeBrightness.size() * 4 * FLOATS_PER_TANGENT
                    ? tangents.toFloatArray() : null;
            var sampling = buildSamplingTopology(samplingCorners.toIntArray(),
                    samplingIds.size(), shadeBrightness.size());
            return new PhotonMesh(vertices.toFloatArray(), spriteBounds.toFloatArray(),
                    shadeBrightness.toFloatArray(), supplied, sampling);
        }

        private static SamplingTopology buildSamplingTopology(int[] corners, int vertexCount, int quadCount) {
            var first = new int[vertexCount];
            java.util.Arrays.fill(first, -1);
            var triangles = new IntArrayList(quadCount * 6);
            for (int q = 0; q < quadCount; q++) {
                int o = q * 4;
                int a = corners[o], b = corners[o + 1], c = corners[o + 2], d = corners[o + 3];
                if (first[a] < 0) first[a] = o;
                if (first[b] < 0) first[b] = o + 1;
                if (first[c] < 0) first[c] = o + 2;
                if (first[d] < 0) first[d] = o + 3;
                triangles.add(a); triangles.add(b); triangles.add(c);
                if (c != d) {
                    triangles.add(c); triangles.add(d); triangles.add(a);
                }
            }
            return new SamplingTopology(first, triangles.toIntArray());
        }
    }
}
