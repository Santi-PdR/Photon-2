package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.lowdragmc.photon.client.gameobject.emitter.data.model.skin.Skeleton;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.skin.VertexAnimationBake;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GltfMeshParserTest {
    @Test
    void parsesEmbeddedGeometryAndBakesNodeTransform() throws Exception {
        String gltf = triangle(false);

        PhotonMesh mesh = GltfMeshParser.parse(
                new ByteArrayInputStream(gltf.getBytes(StandardCharsets.UTF_8)), false);

        assertEquals(1, mesh.quadCount());
        assertEquals(2f, mesh.vertices()[PhotonMesh.vertexOffset(0, 0)], 1e-6f);
        assertEquals(0f, mesh.vertices()[PhotonMesh.vertexOffset(0, 0) + 1], 1e-6f);
        assertEquals(1f, mesh.vertices()[PhotonMesh.vertexOffset(0, 1) + 3], 1e-6f);
    }

    @Test
    void resolvesRelativeBuffersBesideTheModelAndWithinItsNamespace() throws Exception {
        var model = ResourceLocation.fromNamespaceAndPath("example", "models/creature/body.gltf");

        assertEquals(ResourceLocation.fromNamespaceAndPath("example", "models/creature/body.bin"),
                GltfMeshParser.resolveRelativeBufferLocation(model, "body.bin"));
        assertEquals(ResourceLocation.fromNamespaceAndPath("example", "models/shared/weights.bin"),
                GltfMeshParser.resolveRelativeBufferLocation(model, "../shared/weights.bin"));
        assertEquals(ResourceLocation.fromNamespaceAndPath("example", "models/shared/weights.bin"),
                GltfMeshParser.resolveRelativeBufferLocation(model, "%2e%2e/shared/weights.bin"));
    }

    @Test
    void rejectsExternalBufferUrisThatEscapeOrLeaveTheResourceNamespace() {
        var model = ResourceLocation.fromNamespaceAndPath("example", "models/creature/body.gltf");

        assertThrows(java.io.IOException.class,
                () -> GltfMeshParser.resolveRelativeBufferLocation(model, "../../../outside.bin"));
        assertThrows(java.io.IOException.class,
                () -> GltfMeshParser.resolveRelativeBufferLocation(model, "%2e%2e/%2e%2e/%2e%2e/outside.bin"));
        assertThrows(java.io.IOException.class,
                () -> GltfMeshParser.resolveRelativeBufferLocation(model, "..%2f..%2f..%2foutside.bin"));
        assertThrows(java.io.IOException.class,
                () -> GltfMeshParser.resolveRelativeBufferLocation(model, "https://example.invalid/body.bin"));
        assertThrows(java.io.IOException.class,
                () -> GltfMeshParser.resolveRelativeBufferLocation(model, "body.bin?revision=2"));
    }

    @Test
    void readsTheGlbBinaryChunkWhenBufferHasNoUri() throws Exception {
        ByteBuffer binary = ByteBuffer.allocate(9 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0,0,0, 1,0,0, 0,1,0}) binary.putFloat(value);
        String json = """
                {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"}],
                 "bufferViews":[{"buffer":0,"byteLength":36}],"buffers":[{"byteLength":36}]}
                """;

        PhotonMesh mesh = GltfMeshParser.parse(new ByteArrayInputStream(glb(json, binary.array())), false);

        assertEquals(1, mesh.quadCount());
        assertEquals(1f, mesh.vertices()[PhotonMesh.vertexOffset(0, 1)], 1e-6f);
        assertEquals(1f, mesh.vertices()[PhotonMesh.vertexOffset(0, 2) + 1], 1e-6f);
    }

    @Test
    void generatesIndependentFaceNormalsForUnindexedGeometryWithoutNormals() throws Exception {
        ByteBuffer binary = ByteBuffer.allocate(18 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{
                0,0,0, 1,0,0, 0,1,0,
                0,0,0, 0,0,1, 1,0,0
        }) binary.putFloat(value);
        String gltf = """
                {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":6,"type":"VEC3"}],
                 "bufferViews":[{"buffer":0,"byteLength":72}],
                 "buffers":[{"byteLength":72,"uri":"data:application/octet-stream;base64,%s"}]}
                """.formatted(Base64.getEncoder().encodeToString(binary.array()));

        PhotonMesh mesh = GltfMeshParser.parse(
                new ByteArrayInputStream(gltf.getBytes(StandardCharsets.UTF_8)), false);

        assertEquals(2, mesh.quadCount());
        int firstFace = PhotonMesh.vertexOffset(0, 0);
        int secondFace = PhotonMesh.vertexOffset(1, 0);
        assertEquals(0f, mesh.vertices()[firstFace + 5], 1e-6f);
        assertEquals(0f, mesh.vertices()[firstFace + 6], 1e-6f);
        assertEquals(1f, mesh.vertices()[firstFace + 7], 1e-6f);
        assertEquals(0f, mesh.vertices()[secondFace + 5], 1e-6f);
        assertEquals(1f, mesh.vertices()[secondFace + 6], 1e-6f);
        assertEquals(0f, mesh.vertices()[secondFace + 7], 1e-6f);
    }

    @Test
    void parsesAnimationClipsAndSamplesTranslation() throws Exception {
        String gltf = triangle(true);

        var model = GltfMeshParser.parseModel(
                new ByteArrayInputStream(gltf.getBytes(StandardCharsets.UTF_8)), false);

        assertEquals(1, model.mesh().quadCount());
        assertNotNull(model.skeleton());
        // The animated node drives the clip; a second rigid joint carries its unskinned mesh.
        assertEquals(2, model.skeleton().jointCount());
        assertEquals("move", model.clipNames().get(0));
        var clip = model.clip("move");
        assertNotNull(clip);
        assertEquals(1f, clip.duration(), 1e-6f);

        float[] pose = model.skeleton().restTrs().clone();
        clip.sample(0.5f, pose);
        assertEquals(1f, pose[0], 1e-6f);
        assertEquals(0f, pose[1], 1e-6f);
        assertEquals(0f, pose[2], 1e-6f);
    }

    @Test
    void parsesSkinnedGltfAndBakesAnimatedVatFrames() throws Exception {
        var model = GltfMeshParser.parseModel(
                new ByteArrayInputStream(skinnedAnimatedTriangle()), false);

        assertNotNull(model.skeleton());
        assertNotNull(model.skin());
        assertTrue(model.isAnimated());
        var clip = model.clip("move_joint");
        assertNotNull(clip);
        float[] table = VertexAnimationBake.bake(model, clip, 2);

        assertNotNull(table);
        int firstVertex = 0;
        int secondFrame = model.mesh().quadCount() * 4 * VertexAnimationBake.FLOATS_PER_VERTEX;
        assertEquals(0.5f, table[secondFrame + firstVertex] - table[firstVertex], 1e-5f);
    }

    @Test
    void appliesSparsePositionAccessorWithoutBaseBufferView() throws Exception {
        ByteBuffer binary = ByteBuffer.allocate(4 + 9 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        binary.put(new byte[]{0, 1, 2, 0}); // sparse destination indices, padded to four bytes
        for (float value : new float[]{0,0,0, 1,0,0, 0,1,0}) binary.putFloat(value);
        String gltf = """
                {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                 "accessors":[{"componentType":5126,"count":3,"type":"VEC3","sparse":{"count":3,
                   "indices":{"bufferView":0,"componentType":5121},"values":{"bufferView":1}}}],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":3},
                   {"buffer":0,"byteOffset":4,"byteLength":36}],
                 "buffers":[{"byteLength":40,"uri":"data:application/octet-stream;base64,%s"}]}
                """.formatted(Base64.getEncoder().encodeToString(binary.array()));

        PhotonMesh mesh = GltfMeshParser.parse(
                new ByteArrayInputStream(gltf.getBytes(StandardCharsets.UTF_8)), false);

        assertEquals(1, mesh.quadCount());
        assertEquals(1f, mesh.vertices()[PhotonMesh.vertexOffset(0, 1)], 1e-6f);
        assertEquals(1f, mesh.vertices()[PhotonMesh.vertexOffset(0, 2) + 1], 1e-6f);
    }

    @Test
    void appliesSparseIndexAccessor() throws Exception {
        PhotonMesh mesh = GltfMeshParser.parse(
                new ByteArrayInputStream(sparseIndices(new byte[]{0, 1, 2}).getBytes(StandardCharsets.UTF_8)), false);

        assertEquals(1, mesh.quadCount());
        assertEquals(1f, mesh.vertices()[PhotonMesh.vertexOffset(0, 1)], 1e-6f);
        assertEquals(1f, mesh.vertices()[PhotonMesh.vertexOffset(0, 2) + 1], 1e-6f);
    }

    @Test
    void rejectsSparseIndicesThatAreNotSorted() throws Exception {
        PhotonMesh mesh = GltfMeshParser.parse(
                new ByteArrayInputStream(sparseIndices(new byte[]{1, 0, 2}).getBytes(StandardCharsets.UTF_8)), false);

        assertEquals(0, mesh.quadCount());
    }

    @Test
    void gltfSamplingSharesIndexedVerticesAcrossTrianglesWithinOnePrimitive() throws Exception {
        var mesh = GltfMeshParser.parse(new ByteArrayInputStream(indexedQuad(false)), false);
        var topology = mesh.samplingTopology();

        assertEquals(4, topology.vertexCount());
        assertEquals(2, topology.triangleCount());
        assertEquals(topology.triangleVertex(0, 0), topology.triangleVertex(1, 0));
        assertEquals(topology.triangleVertex(0, 2), topology.triangleVertex(1, 1));
    }

    @Test
    void gltfSamplingKeepsPrimitiveInstancesInSeparateTopologyGroups() throws Exception {
        var mesh = GltfMeshParser.parse(new ByteArrayInputStream(indexedQuad(true)), false);

        assertEquals(8, mesh.samplingTopology().vertexCount());
        assertEquals(4, mesh.samplingTopology().triangleCount());
    }

    private static String sparseIndices(byte[] sparseIndices) {
        ByteBuffer binary = ByteBuffer.allocate(36 + 3 + 3).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0,0,0, 1,0,0, 0,1,0}) binary.putFloat(value);
        binary.put(sparseIndices).put(new byte[]{0, 1, 2});
        return """
                {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1}]}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                   {"componentType":5121,"count":3,"type":"SCALAR","sparse":{"count":3,
                     "indices":{"bufferView":1,"componentType":5121},"values":{"bufferView":2}}}],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},
                   {"buffer":0,"byteOffset":36,"byteLength":3},{"buffer":0,"byteOffset":39,"byteLength":3}],
                 "buffers":[{"byteLength":42,"uri":"data:application/octet-stream;base64,%s"}]}
                """.formatted(Base64.getEncoder().encodeToString(binary.array()));
    }

    private static byte[] indexedQuad(boolean duplicatePrimitive) {
        var binary = ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0,0,0, 1,0,0, 1,1,0, 0,1,0}) binary.putFloat(value);
        for (int index : new int[]{0, 1, 2, 0, 2, 3}) binary.putShort((short) index);
        var primitives = duplicatePrimitive
                ? "{\"attributes\":{\"POSITION\":0},\"indices\":1},"
                    + "{\"attributes\":{\"POSITION\":0},\"indices\":1}"
                : "{\"attributes\":{\"POSITION\":0},\"indices\":1}";
        String gltf = """
                {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],
                 "nodes":[{"mesh":0}],"meshes":[{"primitives":[%s]}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":4,"type":"VEC3"},
                   {"bufferView":1,"componentType":5123,"count":6,"type":"SCALAR"}],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":48},
                   {"buffer":0,"byteOffset":48,"byteLength":12}],
                 "buffers":[{"byteLength":60,"uri":"data:application/octet-stream;base64,%s"}]}
                """.formatted(primitives, Base64.getEncoder().encodeToString(binary.array()));
        return gltf.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] skinnedAnimatedTriangle() {
        ByteBuffer binary = ByteBuffer.allocate(252).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0,0,0, 1,0,0, 0,1,0}) binary.putFloat(value);
        for (float value : new float[]{0,0,1, 0,0,1, 0,0,1}) binary.putFloat(value);
        for (float value : new float[]{0,0, 1,0, 0,1}) binary.putFloat(value);
        binary.put(new byte[12]); // JOINTS_0: joint zero for each of the three vertices
        for (int vertex = 0; vertex < 3; vertex++) {
            binary.putFloat(1).putFloat(0).putFloat(0).putFloat(0);
        }
        for (float value : new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1}) binary.putFloat(value);
        binary.putFloat(0).putFloat(1);
        for (float value : new float[]{0,0,0, 1,0,0}) binary.putFloat(value);

        String gltf = """
                {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0,1]}],
                 "nodes":[{"mesh":0,"skin":0},{"translation":[0,0,0]}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"TEXCOORD_0":2,
                   "JOINTS_0":3,"WEIGHTS_0":4}}]}],
                 "skins":[{"skeleton":1,"joints":[1],"inverseBindMatrices":5}],
                 "animations":[{"name":"move_joint","samplers":[{"input":6,"output":7}],
                   "channels":[{"sampler":0,"target":{"node":1,"path":"translation"}}]}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                   {"bufferView":1,"componentType":5126,"count":3,"type":"VEC3"},
                   {"bufferView":2,"componentType":5126,"count":3,"type":"VEC2"},
                   {"bufferView":3,"componentType":5121,"count":3,"type":"VEC4"},
                   {"bufferView":4,"componentType":5126,"count":3,"type":"VEC4"},
                   {"bufferView":5,"componentType":5126,"count":1,"type":"MAT4"},
                   {"bufferView":6,"componentType":5126,"count":2,"type":"SCALAR"},
                   {"bufferView":7,"componentType":5126,"count":2,"type":"VEC3"}],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},
                   {"buffer":0,"byteOffset":36,"byteLength":36},
                   {"buffer":0,"byteOffset":72,"byteLength":24},
                   {"buffer":0,"byteOffset":96,"byteLength":12},
                   {"buffer":0,"byteOffset":108,"byteLength":48},
                   {"buffer":0,"byteOffset":156,"byteLength":64},
                   {"buffer":0,"byteOffset":220,"byteLength":8},
                   {"buffer":0,"byteOffset":228,"byteLength":24}],
                 "buffers":[{"byteLength":252,"uri":"data:application/octet-stream;base64,%s"}]}
                """.formatted(Base64.getEncoder().encodeToString(binary.array()));
        return gltf.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] glb(String json, byte[] binary) {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int jsonLength = (jsonBytes.length + 3) & ~3;
        int binaryLength = (binary.length + 3) & ~3;
        ByteBuffer glb = ByteBuffer.allocate(12 + 8 + jsonLength + 8 + binaryLength).order(ByteOrder.LITTLE_ENDIAN);
        glb.putInt(0x46546C67).putInt(2).putInt(glb.capacity());
        glb.putInt(jsonLength).putInt(0x4E4F534A).put(jsonBytes);
        while (glb.position() < 20 + jsonLength) glb.put((byte) ' ');
        glb.putInt(binaryLength).putInt(0x004E4942).put(binary);
        while (glb.hasRemaining()) glb.put((byte) 0);
        return glb.array();
    }

    private static String triangle(boolean animated) {
        // Three positions, three normals, three UVs, then optional animation times and translations.
        float[] values = animated
                ? new float[]{0,0,0, 1,0,0, 0,1,0, 0,0,1, 0,0,1, 0,0,1,
                    0,0, 1,0, 0,1, 0,1, 0,0,0, 2,0,0}
                : new float[]{0,0,0, 1,0,0, 0,1,0, 0,0,1, 0,0,1, 0,0,1,
                    0,0, 1,0, 0,1};
        ByteBuffer binary = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) binary.putFloat(value);
        int dataLength = binary.capacity();
        String animation = animated ? """
                , {"bufferView":3,"componentType":5126,"count":2,"type":"SCALAR"}
                , {"bufferView":4,"componentType":5126,"count":2,"type":"VEC3"}
                """ : "";
        String views = animated
                ? """
                  , {"buffer":0,"byteOffset":96,"byteLength":8}
                  , {"buffer":0,"byteOffset":104,"byteLength":24}
                  """
                : "";
        String clips = animated ? """
                , "animations":[{"name":"move","samplers":[{"input":3,"output":4}],
                  "channels":[{"sampler":0,"target":{"node":0,"path":"translation"}}]}]
                """ : "";
        String node = animated ? "{\"mesh\":0}" : "{\"mesh\":0,\"translation\":[2,0,0]}";
        return """
                {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],
                 "nodes":[%s],"meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"TEXCOORD_0":2}}]}],
                 "accessors":[
                   {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                   {"bufferView":1,"componentType":5126,"count":3,"type":"VEC3"},
                   {"bufferView":2,"componentType":5126,"count":3,"type":"VEC2"}%s],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},
                   {"buffer":0,"byteOffset":36,"byteLength":36},
                   {"buffer":0,"byteOffset":72,"byteLength":24}%s],
                 "buffers":[{"byteLength":%d,"uri":"data:application/octet-stream;base64,%s"}]%s}
                """.formatted(node, animation, views, dataLength,
                Base64.getEncoder().encodeToString(binary.array()), clips);
    }
}
