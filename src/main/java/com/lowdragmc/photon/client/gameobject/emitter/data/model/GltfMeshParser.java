package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.skin.AnimationClip;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.skin.MeshSkin;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.skin.Skeleton;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.skin.SkinnedModel;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;

/**
 * A glTF 2.0 reader for the geometry Photon actually renders — the same "small parser, no pipeline"
 * choice {@link ObjMeshParser} makes, for the same reason: the loaders that ship with Minecraft and
 * NeoForge are wired to the resource-pack/atlas bakery and cannot hand back raw runtime geometry.
 *
 * <p>Reads <b>.glb</b> (the single-file binary container) and <b>.gltf</b> with embedded data URIs or
 * relative external buffers from the same resource namespace.</p>
 *
 * <p>Sparse accessors are applied over their base data (or a zero-initialized base when no
 * {@code bufferView} is present), with bounds and sorted-index validation. {@link #parse(InputStream, boolean)} reads static geometry, baking the default scene's node
 * hierarchy into the vertices. {@link #parseModel(InputStream, boolean)} additionally reads glTF
 * skins, inverse-bind matrices and translation/rotation/scale animation clips. Both read every
 * {@code TRIANGLES} primitive and the {@code POSITION} / {@code NORMAL} / {@code TEXCOORD_0} /
 * {@code TANGENT} attributes. Materials and textures remain Photon's own; morph targets are not
 * supported yet.</p>
 *
 * <p><b>Tangents.</b> glTF is the first format Photon reads that can carry them, and its convention is
 * already ours: {@code TANGENT} is a {@code vec4}, {@code xyz} the unit tangent and {@code w} the
 * bitangent handedness. A primitive that has them keeps them (baked through the node transform); one
 * that does not falls back to {@link MeshTangents}, which is what the glTF spec asks implementations
 * to do anyway. Mixing the two inside one file makes the whole mesh generate — see
 * {@link PhotonMesh.Builder}.</p>
 *
 * <p>Positions are used <b>raw</b> (no centering or unit conversion), matching the OBJ reader, so the
 * model's size and origin are what the author exported. glTF and Minecraft share a Y-up right-handed
 * convention, so no axis swizzle is applied. glTF's UV origin is already top-left like Minecraft's, so
 * unlike OBJ no V flip is needed — {@code flipV} exists only to rescue an odd export.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class GltfMeshParser {

    private static final int GLB_MAGIC = 0x46546C67;      // "glTF", little-endian
    private static final int CHUNK_JSON = 0x4E4F534A;     // "JSON"
    private static final int CHUNK_BIN = 0x004E4942;      // "BIN\0"
    private static final float[] RIGID_WEIGHTS = {1f, 0f, 0f, 0f};
    /** Malformed files can describe a node cycle; glTF forbids it, so bail rather than recurse forever. */
    private static final int MAX_NODE_DEPTH = 64;

    private GltfMeshParser() {
    }

    public static PhotonMesh parse(InputStream in, boolean flipV) throws IOException {
        return parse(in.readAllBytes(), flipV);
    }

    /** Parse a resource-pack model and resolve its relative buffer URIs through the active resource manager. */
    public static PhotonMesh parse(InputStream in, boolean flipV, ResourceLocation resourceLocation) throws IOException {
        return reader(in.readAllBytes(), flipV, false, uri -> openRelativeBuffer(resourceLocation, uri)).read().mesh();
    }

    /** Package-visible for tests. */
    static PhotonMesh parse(byte[] bytes, boolean flipV) throws IOException {
        return reader(bytes, flipV, false).read().mesh();
    }

    public static SkinnedModel parseModel(InputStream in, boolean flipV) throws IOException {
        return parseModel(in.readAllBytes(), flipV);
    }

    /** Parse a resource-pack model and resolve its relative buffer URIs through the active resource manager. */
    public static SkinnedModel parseModel(InputStream in, boolean flipV, ResourceLocation resourceLocation) throws IOException {
        return reader(in.readAllBytes(), flipV, true, uri -> openRelativeBuffer(resourceLocation, uri)).read();
    }

    static SkinnedModel parseModel(byte[] bytes, boolean flipV) throws IOException {
        return reader(bytes, flipV, true).read();
    }

    private static Reader reader(byte[] bytes, boolean flipV, boolean readSkin) throws IOException {
        return reader(bytes, flipV, readSkin, null);
    }

    @FunctionalInterface
    private interface ExternalBufferResolver {
        byte[] load(String uri) throws IOException;
    }

    private static Reader reader(byte[] bytes, boolean flipV, boolean readSkin,
                                 @org.jetbrains.annotations.Nullable ExternalBufferResolver externalBuffers) throws IOException {
        var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        JsonObject root;
        byte[] glbBin = null;
        if (bytes.length >= 12 && buffer.getInt(0) == GLB_MAGIC) {
            var chunks = readGlbChunks(buffer, bytes.length);
            root = JsonParser.parseString(new String(chunks.json(), StandardCharsets.UTF_8)).getAsJsonObject();
            glbBin = chunks.bin();
        } else {
            root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        return new Reader(root, glbBin, flipV, readSkin, externalBuffers);
    }

    private static byte[] openRelativeBuffer(ResourceLocation model, String value) throws IOException {
        final URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IOException("invalid external buffer URI '" + value + "'", e);
        }
        if (uri.isAbsolute() || uri.getAuthority() != null || uri.getQuery() != null || uri.getFragment() != null
                || uri.getPath() == null || uri.getPath().isEmpty() || uri.getPath().startsWith("/")
                || uri.getPath().contains("\\")) {
            throw new IOException("external buffer URI must be a relative resource path: '" + value + "'");
        }

        var segments = new ArrayDeque<String>();
        String modelPath = model.getPath();
        int lastSlash = modelPath.lastIndexOf('/');
        if (lastSlash >= 0) {
            for (String segment : modelPath.substring(0, lastSlash).split("/")) {
                if (!segment.isEmpty()) segments.addLast(segment);
            }
        }
        for (String segment : uri.getPath().split("/")) {
            if (segment.isEmpty() || segment.equals(".")) continue;
            if (segment.equals("..")) {
                if (segments.isEmpty()) throw new IOException("external buffer URI escapes its resource namespace: '" + value + "'");
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        if (segments.isEmpty()) throw new IOException("external buffer URI resolves to an empty resource path: '" + value + "'");

        String path = String.join("/", segments);
        final ResourceLocation buffer;
        try {
            buffer = ResourceLocation.fromNamespaceAndPath(model.getNamespace(), path);
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid external buffer resource path '" + path + "'", e);
        }
        try (var input = Minecraft.getInstance().getResourceManager().open(buffer)) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new IOException("could not load external buffer '" + buffer + "' referenced by '" + model + "'", e);
        }
    }

    private record GlbChunks(byte[] json, byte[] bin) {
    }

    /**
     * GLB is a 12-byte header (magic / version / total length) followed by length-prefixed chunks. Only
     * the first JSON chunk and the first BIN chunk are meaningful; anything else is an extension chunk
     * and is skipped, which is exactly what the spec tells readers to do.
     */
    private static GlbChunks readGlbChunks(ByteBuffer buffer, int length) throws IOException {
        int version = buffer.getInt(4);
        if (version != 2) {
            throw new IOException("unsupported glb version " + version + " (only glTF 2.0)");
        }
        byte[] json = null;
        byte[] bin = null;
        int offset = 12;
        while (offset + 8 <= length) {
            int chunkLength = buffer.getInt(offset);
            int chunkType = buffer.getInt(offset + 4);
            int dataStart = offset + 8;
            // `chunkLength > length - dataStart`, not `dataStart + chunkLength > length`: the latter
            // wraps negative for a huge chunkLength, letting a corrupt file through to a
            // `new byte[chunkLength]` OutOfMemoryError — an Error, which the loader's catch(Exception)
            // would not contain.
            if (chunkLength < 0 || chunkLength > length - dataStart) {
                throw new IOException("truncated glb chunk at offset " + offset);
            }
            if (chunkType == CHUNK_JSON && json == null) {
                json = new byte[chunkLength];
                buffer.get(dataStart, json);
            } else if (chunkType == CHUNK_BIN && bin == null) {
                bin = new byte[chunkLength];
                buffer.get(dataStart, bin);
            }
            offset = dataStart + chunkLength;
        }
        if (json == null) {
            throw new IOException("glb has no JSON chunk");
        }
        return new GlbChunks(json, bin);
    }

    /** One parse. Holds the decoded buffers so accessors can be read lazily as primitives need them. */
    private static final class Reader {
        private final JsonObject root;
        private final byte[] glbBin;
        private final boolean flipV;
        private final boolean readSkin;
        @org.jetbrains.annotations.Nullable
        private final ExternalBufferResolver externalBuffers;
        private final PhotonMesh.Builder builder = new PhotonMesh.Builder();
        private final List<byte[]> buffers = new ArrayList<>();

        // reused per triangle corner so a large mesh doesn't allocate per vertex
        private final float[][] corner = new float[3][PhotonMesh.FLOATS_PER_VERTEX];
        private final float[][] cornerTangent = new float[3][PhotonMesh.FLOATS_PER_TANGENT];
        private final Vector3f scratch = new Vector3f();
        private Skeleton skeleton;
        private Int2IntOpenHashMap jointSlotOfNode;
        private Int2IntOpenHashMap rigidSlotOfNode;
        private IntArrayList skinJoints = new IntArrayList();
        private FloatArrayList skinWeights = new FloatArrayList();

        Reader(JsonObject root, byte[] glbBin, boolean flipV, boolean readSkin,
               @org.jetbrains.annotations.Nullable ExternalBufferResolver externalBuffers) {
            this.root = root;
            this.glbBin = glbBin;
            this.flipV = flipV;
            this.readSkin = readSkin;
            this.externalBuffers = externalBuffers;
        }

        SkinnedModel read() throws IOException {
            decodeBuffers();
            var nodes = array("nodes");
            buildSkeleton(nodes);
            var scenes = array("scenes");
            var roots = new ArrayList<Integer>();
            int sceneIndex = root.has("scene") ? root.get("scene").getAsInt() : 0;
            if (sceneIndex >= 0 && sceneIndex < scenes.size()) {
                var sceneNodes = scenes.get(sceneIndex).getAsJsonObject().getAsJsonArray("nodes");
                if (sceneNodes != null) sceneNodes.forEach(n -> roots.add(n.getAsInt()));
            }
            if (roots.isEmpty()) {
                // No scene graph, an empty scene, or a `scene` index pointing past the array — all of
                // which used to yield an invisible model with nothing in the log. Fall back to every node
                // that nobody lists as a child, so the hierarchy is still walked exactly once.
                var children = new HashSet<Integer>();
                for (var node : nodes) {
                    var kids = node.getAsJsonObject().getAsJsonArray("children");
                    if (kids != null) kids.forEach(k -> children.add(k.getAsInt()));
                }
                for (int i = 0; i < nodes.size(); i++) {
                    if (!children.contains(i)) roots.add(i);
                }
            }
            for (int nodeIndex : roots) {
                visitNode(nodes, nodeIndex, new Matrix4f(), 0, -1);
            }
            PhotonMesh mesh = builder.build();
            if (skeleton == null) return SkinnedModel.staticModel(mesh);
            int vertexCount = mesh.quadCount() * 4;
            while (skinJoints.size() < vertexCount * MeshSkin.INFLUENCES) {
                skinJoints.add(0);
                skinWeights.add(0f);
            }
            MeshSkin skin = mesh.isEmpty() || skinJoints.isEmpty()
                    ? null : new MeshSkin(skinJoints.toIntArray(), skinWeights.toFloatArray());
            return new SkinnedModel(mesh, skin, skeleton, readAnimations());
        }

        /** Build the joints named by every skin plus their transform ancestors. */
        private void buildSkeleton(JsonArray nodes) {
            if (!readSkin) return;
            JsonArray skins = array("skins");
            JsonArray animations = array("animations");
            if (skins.isEmpty() && animations.isEmpty()) return;
            int[] parentOf = new int[nodes.size()];
            Arrays.fill(parentOf, -1);
            for (int i = 0; i < nodes.size(); i++) {
                JsonArray children = nodes.get(i).getAsJsonObject().getAsJsonArray("children");
                if (children != null) for (JsonElement child : children) {
                    int index = child.getAsInt();
                    if (index >= 0 && index < parentOf.length) parentOf[index] = i;
                }
            }

            Skeleton.Builder skeletonBuilder = new Skeleton.Builder();
            float[] trs = new float[Skeleton.FLOATS_PER_TRS];
            for (JsonElement entry : skins) {
                JsonArray joints = entry.getAsJsonObject().getAsJsonArray("joints");
                if (joints == null) continue;
                for (JsonElement joint : joints) {
                    addJointChain(skeletonBuilder, nodes, parentOf, joint.getAsInt(), trs);
                }
            }
            // Animation-only glTF files still need a joint-name map so their clips can be retargeted.
            java.util.Set<Integer> animated = animatedNodes(animations);
            for (int animatedNode : animated) {
                addJointChain(skeletonBuilder, nodes, parentOf, animatedNode, trs);
            }
            IntArrayList rigidNodes = new IntArrayList();
            float[] identityTrs = {0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f};
            for (int node = 0; node < nodes.size(); node++) {
                JsonObject json = nodes.get(node).getAsJsonObject();
                if (!json.has("mesh") || json.has("skin") || !isAnimatedInTree(node, parentOf, animated)) continue;
                addJointChain(skeletonBuilder, nodes, parentOf, node, trs);
                skeletonBuilder.joint(rigidKey(node, nodes.size()), node, "rigid" + node, identityTrs, 0);
                rigidNodes.add(node);
            }
            if (skeletonBuilder.jointCount() == 0) return;

            Skeleton[] result = new Skeleton[1];
            int[] remap = skeletonBuilder.sortInto(result);
            skeleton = result[0];
            jointSlotOfNode = new Int2IntOpenHashMap();
            jointSlotOfNode.defaultReturnValue(-1);
            for (int node = 0; node < nodes.size(); node++) {
                int slot = skeletonBuilder.slot(node);
                if (slot >= 0) jointSlotOfNode.put(node, remap[slot]);
            }
            if (!rigidNodes.isEmpty()) {
                rigidSlotOfNode = new Int2IntOpenHashMap();
                rigidSlotOfNode.defaultReturnValue(-1);
                for (int node : rigidNodes) {
                    int slot = skeletonBuilder.slot(rigidKey(node, nodes.size()));
                    if (slot >= 0) rigidSlotOfNode.put(node, remap[slot]);
                }
            }

            float[] affine = new float[Skeleton.FLOATS_PER_MATRIX];
            for (JsonElement entry : skins) {
                JsonObject skin = entry.getAsJsonObject();
                JsonArray joints = skin.getAsJsonArray("joints");
                if (joints == null || !skin.has("inverseBindMatrices")) continue;
                float[] matrices = readAccessor(skin.get("inverseBindMatrices").getAsInt(), 16);
                if (matrices == null) continue;
                for (int j = 0; j < joints.size() && (j + 1) * 16 <= matrices.length; j++) {
                    int slot = jointSlotOfNode.get(joints.get(j).getAsInt());
                    if (slot < 0) continue;
                    Skeleton.fromColumnMajor4x4(affine, 0, matrices, j * 16);
                    System.arraycopy(affine, 0, skeleton.inverseBind(),
                            slot * Skeleton.FLOATS_PER_MATRIX, Skeleton.FLOATS_PER_MATRIX);
                }
            }
        }

        private static java.util.Set<Integer> animatedNodes(JsonArray animations) {
            java.util.Set<Integer> animated = new HashSet<>();
            for (JsonElement animation : animations) {
                JsonArray channels = animation.getAsJsonObject().getAsJsonArray("channels");
                if (channels == null) continue;
                for (JsonElement element : channels) {
                    JsonObject target = element.getAsJsonObject().getAsJsonObject("target");
                    if (target == null || !target.has("node") || !target.has("path")) continue;
                    String path = target.get("path").getAsString();
                    if (path.equals("translation") || path.equals("rotation") || path.equals("scale")) {
                        animated.add(target.get("node").getAsInt());
                    }
                }
            }
            return animated;
        }

        private static boolean isAnimatedInTree(int node, int[] parentOf, java.util.Set<Integer> animated) {
            for (int depth = 0; node >= 0 && node < parentOf.length && depth <= MAX_NODE_DEPTH; depth++) {
                if (animated.contains(node)) return true;
                node = parentOf[node];
            }
            return false;
        }

        private static int rigidKey(int node, int nodeCount) {
            return nodeCount + node;
        }

        private void addJointChain(Skeleton.Builder target, JsonArray nodes, int[] parentOf, int node, float[] trs) {
            for (int depth = 0; node >= 0 && node < nodes.size() && depth <= MAX_NODE_DEPTH; depth++) {
                if (target.has(node)) return;
                JsonObject json = nodes.get(node).getAsJsonObject();
                nodeTrs(json, trs);
                target.joint(node, parentOf[node], json.has("name") ? json.get("name").getAsString() : "joint" + node,
                        trs, 0);
                node = parentOf[node];
            }
        }

        private static void nodeTrs(JsonObject node, float[] out) {
            Matrix4f matrix = localTransform(node);
            Vector3f translation = matrix.getTranslation(new Vector3f());
            Quaternionf rotation = matrix.getNormalizedRotation(new Quaternionf());
            Vector3f scale = matrix.getScale(new Vector3f());
            out[0] = translation.x; out[1] = translation.y; out[2] = translation.z;
            out[3] = rotation.x; out[4] = rotation.y; out[5] = rotation.z; out[6] = rotation.w;
            out[7] = scale.x; out[8] = scale.y; out[9] = scale.z;
        }

        private List<AnimationClip> readAnimations() {
            JsonArray animations = array("animations");
            List<AnimationClip> clips = new ArrayList<>();
            for (int i = 0; i < animations.size(); i++) {
                JsonObject animation = animations.get(i).getAsJsonObject();
                JsonArray channels = animation.getAsJsonArray("channels");
                JsonArray samplers = animation.getAsJsonArray("samplers");
                List<AnimationClip.Channel> parsed = new ArrayList<>();
                if (channels != null && samplers != null) for (JsonElement entry : channels) {
                    AnimationClip.Channel channel = readChannel(entry.getAsJsonObject(), samplers);
                    if (channel != null) parsed.add(channel);
                }
                if (!parsed.isEmpty()) clips.add(new AnimationClip(
                        animation.has("name") ? animation.get("name").getAsString() : "animation" + i, parsed));
            }
            return List.copyOf(clips);
        }

        private AnimationClip.Channel readChannel(JsonObject channel, JsonArray samplers) {
            JsonObject target = channel.getAsJsonObject("target");
            if (target == null || !target.has("node") || !target.has("path") || jointSlotOfNode == null) return null;
            int joint = jointSlotOfNode.get(target.get("node").getAsInt());
            if (joint < 0) return null;
            AnimationClip.Path path = switch (target.get("path").getAsString()) {
                case "translation" -> AnimationClip.Path.TRANSLATION;
                case "rotation" -> AnimationClip.Path.ROTATION;
                case "scale" -> AnimationClip.Path.SCALE;
                default -> null;
            };
            if (path == null || !channel.has("sampler")) return null;
            int samplerIndex = channel.get("sampler").getAsInt();
            if (samplerIndex < 0 || samplerIndex >= samplers.size()) return null;
            JsonObject sampler = samplers.get(samplerIndex).getAsJsonObject();
            if (!sampler.has("input") || !sampler.has("output")) return null;
            AnimationClip.Interpolation interpolation = switch (sampler.has("interpolation")
                    ? sampler.get("interpolation").getAsString() : "LINEAR") {
                case "STEP" -> AnimationClip.Interpolation.STEP;
                case "CUBICSPLINE" -> AnimationClip.Interpolation.CUBICSPLINE;
                default -> AnimationClip.Interpolation.LINEAR;
            };
            float[] times = readAccessor(sampler.get("input").getAsInt(), 1);
            int components = path == AnimationClip.Path.ROTATION ? 4 : 3;
            float[] values = readAccessor(sampler.get("output").getAsInt(), components);
            if (times == null || values == null || times.length == 0) return null;
            int expected = times.length * components * (interpolation == AnimationClip.Interpolation.CUBICSPLINE ? 3 : 1);
            if (values.length < expected) return null;
            return new AnimationClip.Channel(joint, path, interpolation, times, values);
        }

        /** Walk the hierarchy, composing transforms so each primitive is emitted in scene space. */
        private void visitNode(JsonArray nodes, int index, Matrix4f parent, int depth, int inheritedSkin) {
            if (depth > MAX_NODE_DEPTH || index < 0 || index >= nodes.size()) return;
            var node = nodes.get(index).getAsJsonObject();
            var world = new Matrix4f(parent).mul(localTransform(node));
            if (node.has("mesh")) {
                int skinIndex = readSkin && node.has("skin") ? node.get("skin").getAsInt() : inheritedSkin;
                int rigidSlot = rigidSlotOfNode == null ? -1 : rigidSlotOfNode.get(index);
                boolean posed = skinIndex >= 0 || rigidSlot >= 0;
                readMesh(node.get("mesh").getAsInt(), posed ? new Matrix4f() : world, skinIndex, rigidSlot);
            }
            var children = node.getAsJsonArray("children");
            if (children != null) {
                for (var child : children) {
                    visitNode(nodes, child.getAsInt(), world, depth + 1, inheritedSkin);
                }
            }
        }

        /** A node is either a full column-major {@code matrix} or a translation/rotation/scale triple. */
        private static Matrix4f localTransform(JsonObject node) {
            var m = node.has("matrix") ? node.getAsJsonArray("matrix") : null;
            if (m != null && m.size() >= 16) {
                var values = new float[16];
                for (int i = 0; i < 16; i++) values[i] = m.get(i).getAsFloat();
                return new Matrix4f().set(values);
            }
            // A short matrix array would zero-pad into a singular transform that collapses the node's
            // geometry to the origin. Identity leaves the model diagnosable instead.
            if (m != null) {
                return new Matrix4f();
            }
            var matrix = new Matrix4f();
            var t = node.getAsJsonArray("translation");
            if (t != null) {
                matrix.translate(t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat());
            }
            var r = node.getAsJsonArray("rotation"); // glTF stores the quaternion as (x, y, z, w)
            if (r != null) {
                matrix.rotate(new Quaternionf(r.get(0).getAsFloat(), r.get(1).getAsFloat(),
                        r.get(2).getAsFloat(), r.get(3).getAsFloat()));
            }
            var s = node.getAsJsonArray("scale");
            if (s != null) {
                matrix.scale(s.get(0).getAsFloat(), s.get(1).getAsFloat(), s.get(2).getAsFloat());
            }
            return matrix;
        }

        private void readMesh(int meshIndex, Matrix4f world, int skinIndex, int rigidSlot) {
            var meshes = array("meshes");
            if (meshIndex < 0 || meshIndex >= meshes.size()) return;
            int[] jointSlots = skinJointSlots(skinIndex);
            float determinant = world.determinant3x3();
            // Normals need the inverse transpose (non-uniform scale skews them); tangents are plain
            // directions and use the matrix itself, per the glTF spec. A singular transform — a zero
            // scale axis, which is how exporters hide a node — makes the inverse transpose Inf/NaN, so
            // pass the file's own normals through untransformed rather than let NaN reach the VBO.
            var normalMatrix = Float.isFinite(determinant) && Math.abs(determinant) > 1.0e-12f
                    ? world.normal(new Matrix3f())
                    : new Matrix3f();
            // A negative determinant mirrors the node: it flips which way the bitangent points AND
            // reverses the triangle winding (glTF 3.7.2.1). Skipping the winding leaves a mirrored
            // instance back-facing, i.e. culled away entirely — looks like a failed load, not a bug.
            boolean mirrored = determinant < 0f;
            var primitives = meshes.get(meshIndex).getAsJsonObject().getAsJsonArray("primitives");
            if (primitives == null) return;
            for (var element : primitives) {
                readPrimitive(element.getAsJsonObject(), world, normalMatrix, mirrored, jointSlots, rigidSlot);
            }
        }

        private int[] skinJointSlots(int skinIndex) {
            if (skinIndex < 0 || jointSlotOfNode == null) return null;
            JsonArray skins = array("skins");
            if (skinIndex >= skins.size()) return null;
            JsonArray joints = skins.get(skinIndex).getAsJsonObject().getAsJsonArray("joints");
            if (joints == null) return null;
            int[] slots = new int[joints.size()];
            for (int i = 0; i < slots.length; i++) slots[i] = jointSlotOfNode.get(joints.get(i).getAsInt());
            return slots;
        }

        private void readPrimitive(JsonObject primitive, Matrix4f world, Matrix3f normalMatrix,
                                   boolean mirrored, int[] jointSlots, int rigidSlot) {
            int mode = primitive.has("mode") ? primitive.get("mode").getAsInt() : 4;
            if (mode != 4) {
                // 4 = TRIANGLES. Strips/fans/points/lines are legal glTF but essentially never exported
                // for meshes; skipping is better than guessing a winding.
                return;
            }
            var attributes = primitive.getAsJsonObject("attributes");
            if (attributes == null || !attributes.has("POSITION")) return;

            float[] positions = readAccessor(attributes.get("POSITION").getAsInt(), 3);
            if (positions == null) return;
            int vertexCount = positions.length / 3;
            float[] normals = attributeOf(attributes, "NORMAL", 3, vertexCount);
            float[] uvs = attributeOf(attributes, "TEXCOORD_0", 2, vertexCount);
            float[] tangents = attributeOf(attributes, "TANGENT", 4, vertexCount);
            float[] jointIndices = jointSlots == null ? null : attributeOf(attributes, "JOINTS_0", 4, vertexCount);
            float[] jointWeights = jointIndices == null ? null : attributeOf(attributes, "WEIGHTS_0", 4, vertexCount);
            if (jointWeights == null) jointIndices = null;

            int[] indices = primitive.has("indices")
                    ? readIndices(primitive.get("indices").getAsInt())
                    : sequence(vertexCount);
            if (indices == null) return;

            // A primitive instance has independent topology even if the same glTF mesh is instanced
            // by several nodes with different world transforms.
            int samplingGroup = builder.newSamplingGroup();
            float handedness = mirrored ? -1f : 1f;
            for (int i = 0; i + 2 < indices.length; i += 3) {
                boolean ok = true;
                int[] sourceVertices = new int[3];
                for (int k = 0; k < 3; k++) {
                    // corners 1 and 2 swap on a mirrored node, restoring the front face
                    int src = (mirrored && k > 0) ? 3 - k : k;
                    sourceVertices[k] = indices[i + src];
                    ok &= fillCorner(k, sourceVertices[k], vertexCount, positions, normals, uvs, tangents,
                            world, normalMatrix, handedness);
                }
                if (!ok) continue;
                if (normals == null) {
                    faceNormal();
                }
                int sampleA = builder.samplingVertex(samplingGroup, sourceVertices[0]);
                int sampleB = builder.samplingVertex(samplingGroup, sourceVertices[1]);
                int sampleC = builder.samplingVertex(samplingGroup, sourceVertices[2]);
                if (tangents == null) {
                    builder.triangle(corner[0], corner[1], corner[2], sampleA, sampleB, sampleC);
                } else {
                    builder.triangle(corner[0], corner[1], corner[2],
                            cornerTangent[0], cornerTangent[1], cornerTangent[2], sampleA, sampleB, sampleC);
                }
                if (jointIndices != null) {
                    addSkin(sourceVertices[0], jointIndices, jointWeights, jointSlots);
                    addSkin(sourceVertices[1], jointIndices, jointWeights, jointSlots);
                    addSkin(sourceVertices[2], jointIndices, jointWeights, jointSlots);
                    addSkin(sourceVertices[2], jointIndices, jointWeights, jointSlots); // Photon stores triangles as degenerate quads
                } else if (rigidSlot >= 0) {
                    addRigidSkin(rigidSlot);
                    addRigidSkin(rigidSlot);
                    addRigidSkin(rigidSlot);
                    addRigidSkin(rigidSlot);
                } else if (skeleton != null) {
                    padSkinTo(skinJoints.size() / MeshSkin.INFLUENCES + 4);
                }
            }
        }

        private void addRigidSkin(int joint) {
            skinJoints.add(joint);
            skinJoints.add(0);
            skinJoints.add(0);
            skinJoints.add(0);
            skinWeights.addElements(skinWeights.size(), RIGID_WEIGHTS);
        }

        private void addSkin(int vertex, float[] jointIndices, float[] weights, int[] jointSlots) {
            int source = vertex * MeshSkin.INFLUENCES;
            int[] mapped = new int[MeshSkin.INFLUENCES];
            float[] normalized = new float[MeshSkin.INFLUENCES];
            float total = 0f;
            for (int i = 0; i < MeshSkin.INFLUENCES; i++) {
                int local = (int) jointIndices[source + i];
                int joint = local >= 0 && local < jointSlots.length ? jointSlots[local] : -1;
                float weight = weights[source + i];
                if (joint < 0 || !(weight > 0f) || !Float.isFinite(weight)) continue;
                mapped[i] = joint;
                normalized[i] = weight;
                total += weight;
            }
            if (total > 0f && Math.abs(total - 1f) > 1.0e-4f) {
                for (int i = 0; i < normalized.length; i++) normalized[i] /= total;
            }
            skinJoints.addElements(skinJoints.size(), mapped);
            skinWeights.addElements(skinWeights.size(), normalized);
        }

        private void padSkinTo(int vertexCount) {
            while (skinJoints.size() < vertexCount * MeshSkin.INFLUENCES) {
                skinJoints.add(0);
                skinWeights.add(0f);
            }
        }

        /** Decode one indexed vertex into {@link #corner}/{@link #cornerTangent}, in scene space. */
        private boolean fillCorner(int slot, int vertex, int vertexCount, float[] positions,
                                   float[] normals, float[] uvs, float[] tangents,
                                   Matrix4f world, Matrix3f normalMatrix, float handedness) {
            if (vertex < 0 || vertex >= vertexCount) return false;
            var out = corner[slot];
            world.transformPosition(scratch.set(positions[vertex * 3], positions[vertex * 3 + 1],
                    positions[vertex * 3 + 2]));
            out[0] = scratch.x;
            out[1] = scratch.y;
            out[2] = scratch.z;

            float u = uvs == null ? 0f : uvs[vertex * 2];
            float v = uvs == null ? 0f : uvs[vertex * 2 + 1];
            out[3] = u;
            out[4] = flipV ? 1f - v : v;

            if (normals != null) {
                scratch.set(normals[vertex * 3], normals[vertex * 3 + 1], normals[vertex * 3 + 2])
                        .mul(normalMatrix);
                if (isDegenerate(scratch)) scratch.set(0f, 1f, 0f);
                else scratch.normalize();
                out[5] = scratch.x;
                out[6] = scratch.y;
                out[7] = scratch.z;
            }

            if (tangents != null) {
                var t = cornerTangent[slot];
                world.transformDirection(scratch.set(tangents[vertex * 4], tangents[vertex * 4 + 1],
                        tangents[vertex * 4 + 2]));
                if (isDegenerate(scratch)) scratch.set(1f, 0f, 0f);
                else scratch.normalize();
                t[0] = scratch.x;
                t[1] = scratch.y;
                t[2] = scratch.z;
                t[3] = (tangents[vertex * 4 + 3] < 0f ? -1f : 1f) * handedness;
            }
            return true;
        }

        /**
         * True when v cannot be normalized — zero length, or non-finite. The finite test is the load-bearing
         * half: NaN fails a bare {@code > epsilon} check, so a magnitude test alone would skip normalize()
         * and let the NaN through into the vertex buffer, the weld keys and the shader.
         */
        private static boolean isDegenerate(Vector3f v) {
            float len2 = v.lengthSquared();
            return !Float.isFinite(len2) || len2 <= 1.0e-20f;
        }

        /** Newell's normal of the current triangle, for a primitive that shipped without NORMAL. */
        private void faceNormal() {
            float nx = 0, ny = 0, nz = 0;
            for (int i = 0; i < 3; i++) {
                var cur = corner[i];
                var next = corner[(i + 1) % 3];
                nx += (cur[1] - next[1]) * (cur[2] + next[2]);
                ny += (cur[2] - next[2]) * (cur[0] + next[0]);
                nz += (cur[0] - next[0]) * (cur[1] + next[1]);
            }
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0e-6f) {
                nx = 0;
                ny = 0;
                nz = 1;
                len = 1;
            }
            for (var c : corner) {
                c[5] = nx / len;
                c[6] = ny / len;
                c[7] = nz / len;
            }
        }

        private float[] attributeOf(JsonObject attributes, String name, int components, int vertexCount) {
            if (!attributes.has(name)) return null;
            float[] values = readAccessor(attributes.get(name).getAsInt(), components);
            // An attribute shorter than POSITION is a malformed file; dropping it beats reading past it.
            return values != null && values.length >= vertexCount * components ? values : null;
        }

        private static int[] sequence(int count) {
            var out = new int[count];
            for (int i = 0; i < count; i++) out[i] = i;
            return out;
        }

        // ---- buffers / accessors -------------------------------------------------------------

        private void decodeBuffers() throws IOException {
            var declared = array("buffers");
            for (int i = 0; i < declared.size(); i++) {
                var buffer = declared.get(i).getAsJsonObject();
                if (!buffer.has("uri")) {
                    // No uri = the GLB binary chunk, which by spec can only be buffer 0.
                    if (i == 0 && glbBin != null) {
                        buffers.add(glbBin);
                        continue;
                    }
                    throw new IOException("buffer " + i + " has no uri and there is no glb binary chunk");
                }
                String uri = buffer.get("uri").getAsString();
                if (!uri.startsWith("data:")) {
                    if (externalBuffers == null) {
                        throw new IOException("buffer " + i + " points at external file '" + uri
                                + "' but no model resource location was supplied");
                    }
                    buffers.add(externalBuffers.load(uri));
                    continue;
                }
                int comma = uri.indexOf(',');
                if (comma < 0 || uri.lastIndexOf("base64", comma) < 0) {
                    throw new IOException("buffer " + i + " uses a non-base64 data uri");
                }
                try {
                    buffers.add(Base64.getDecoder().decode(uri.substring(comma + 1)));
                } catch (IllegalArgumentException e) {
                    throw new IOException("buffer " + i + " has invalid base64 data", e);
                }
            }
            for (int i = 0; i < declared.size(); i++) {
                JsonObject buffer = declared.get(i).getAsJsonObject();
                if (buffer.has("byteLength") && buffer.get("byteLength").getAsInt() > buffers.get(i).length) {
                    throw new IOException("buffer " + i + " is shorter than its declared byteLength");
                }
            }
        }

        /** An accessor's values as floats, {@code components} per element, or null if unreadable. */
        private float[] readAccessor(int index, int components) {
            var accessors = array("accessors");
            if (index < 0 || index >= accessors.size()) return null;
            var accessor = accessors.get(index).getAsJsonObject();
            int count = accessor.get("count").getAsInt();
            int componentType = accessor.get("componentType").getAsInt();
            int declared = componentsOf(accessor.get("type").getAsString());
            if (declared != components || count <= 0) return null;
            boolean normalized = accessor.has("normalized") && accessor.get("normalized").getAsBoolean();
            int componentSize = componentSize(componentType);
            if (componentSize == 0) return null;

            var out = new float[count * components];
            if (accessor.has("bufferView")) {
                var view = bufferView(accessor);
                if (view == null) return null;
                int stride = view.stride() > 0 ? view.stride() : components * componentSize;
                int base = view.offset() + (accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0);
                var data = view.data();
                for (int i = 0; i < count; i++) {
                    long element = (long) base + (long) i * stride;
                    for (int c = 0; c < components; c++) {
                        long at = element + (long) c * componentSize;
                        if (at < view.offset() || at + componentSize > view.limit()) return null;
                        out[i * components + c] = readComponent(data, (int) at, componentType, normalized);
                    }
                }
            }
            if (accessor.has("sparse") && !applySparse(accessor, out, components, componentType, componentSize, normalized)) return null;
            return out;
        }

        private int[] readIndices(int index) {
            var accessors = array("accessors");
            if (index < 0 || index >= accessors.size()) return null;
            var accessor = accessors.get(index).getAsJsonObject();
            if (!"SCALAR".equals(accessor.has("type") ? accessor.get("type").getAsString() : "")) return null;
            int count = accessor.get("count").getAsInt();
            int componentType = accessor.get("componentType").getAsInt();
            int componentSize = componentSize(componentType);
            if ((componentType != 5121 && componentType != 5123 && componentType != 5125) || count <= 0) return null;
            var out = new int[count];
            if (accessor.has("bufferView")) {
                var view = bufferView(accessor);
                if (view == null) return null;
                int stride = view.stride() > 0 ? view.stride() : componentSize;
                int base = view.offset() + (accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0);
                var data = view.data();
                for (int i = 0; i < count; i++) {
                    long at = (long) base + (long) i * stride;
                    if (at < view.offset() || at + componentSize > view.limit()) return null;
                    out[i] = readIndex(data, (int) at, componentType);
                    if (out[i] < 0) return null;
                }
            }
            if (accessor.has("sparse") && !applySparseIndices(accessor, out, componentType)) return null;
            return out;
        }

        private boolean applySparse(JsonObject accessor, float[] out, int components,
                                    int componentType, int componentSize, boolean normalized) {
            try {
                JsonObject sparse = accessor.getAsJsonObject("sparse");
                int count = sparse.get("count").getAsInt();
                if (count < 0 || count > out.length / components) return false;
                JsonObject indices = sparse.getAsJsonObject("indices");
                int indexType = indices.get("componentType").getAsInt();
                if (indexType != 5121 && indexType != 5123 && indexType != 5125) return false;
                View indexView = bufferView(indices.get("bufferView").getAsInt());
                View valueView = bufferView(sparse.getAsJsonObject("values").get("bufferView").getAsInt());
                if (indexView == null || valueView == null) return false;
                int indexOffset = indexView.offset() + optionalOffset(indices, "byteOffset");
                JsonObject values = sparse.getAsJsonObject("values");
                int valueOffset = valueView.offset() + optionalOffset(values, "byteOffset");
                ByteBuffer indexData = indexView.data();
                ByteBuffer valueData = valueView.data();
                int previous = -1;
                for (int i = 0; i < count; i++) {
                    long indexAt = (long) indexOffset + (long) i * componentSize(indexType);
                    long valueAt = (long) valueOffset + (long) i * components * componentSize;
                    if (indexAt < indexView.offset() || indexAt + componentSize(indexType) > indexView.limit()
                            || valueAt < valueView.offset()
                            || valueAt + (long) components * componentSize > valueView.limit()) return false;
                    int target = readIndex(indexData, (int) indexAt, indexType);
                    if (target <= previous || target >= out.length / components) return false;
                    previous = target;
                    for (int c = 0; c < components; c++) {
                        out[target * components + c] = readComponent(valueData,
                                (int) valueAt + c * componentSize, componentType, normalized);
                    }
                }
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        }

        private boolean applySparseIndices(JsonObject accessor, int[] out, int componentType) {
            try {
                JsonObject sparse = accessor.getAsJsonObject("sparse");
                int count = sparse.get("count").getAsInt();
                if (count < 0 || count > out.length) return false;
                JsonObject indices = sparse.getAsJsonObject("indices");
                int indexType = indices.get("componentType").getAsInt();
                if (indexType != 5121 && indexType != 5123 && indexType != 5125) return false;
                View indexView = bufferView(indices.get("bufferView").getAsInt());
                View valueView = bufferView(sparse.getAsJsonObject("values").get("bufferView").getAsInt());
                if (indexView == null || valueView == null) return false;
                int indexOffset = indexView.offset() + optionalOffset(indices, "byteOffset");
                int valueOffset = valueView.offset() + optionalOffset(sparse.getAsJsonObject("values"), "byteOffset");
                ByteBuffer indexData = indexView.data();
                ByteBuffer valueData = valueView.data();
                int previous = -1;
                int valueSize = componentSize(componentType);
                for (int i = 0; i < count; i++) {
                    long indexAt = (long) indexOffset + (long) i * componentSize(indexType);
                    long valueAt = (long) valueOffset + (long) i * valueSize;
                    if (indexAt < indexView.offset() || indexAt + componentSize(indexType) > indexView.limit()
                            || valueAt < valueView.offset() || valueAt + valueSize > valueView.limit()) return false;
                    int target = readIndex(indexData, (int) indexAt, indexType);
                    int value = readIndex(valueData, (int) valueAt, componentType);
                    if (target <= previous || target >= out.length || value < 0) return false;
                    previous = target;
                    out[target] = value;
                }
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        }

        private static int optionalOffset(JsonObject object, String field) {
            return object.has(field) ? object.get(field).getAsInt() : 0;
        }

        private static int readIndex(ByteBuffer data, int at, int componentType) {
            return switch (componentType) {
                case 5121 -> data.get(at) & 0xFF;
                case 5123 -> data.getShort(at) & 0xFFFF;
                case 5125 -> {
                    int value = data.getInt(at);
                    yield value < 0 ? -1 : value;
                }
                default -> -1;
            };
        }

        private record View(ByteBuffer data, int offset, int stride, int limit) {
        }

        private View bufferView(JsonObject accessor) {
            if (!accessor.has("bufferView")) return null;
            return bufferView(accessor.get("bufferView").getAsInt());
        }

        private View bufferView(int index) {
            var views = array("bufferViews");
            if (index < 0 || index >= views.size()) return null;
            var view = views.get(index).getAsJsonObject();
            int bufferIndex = view.get("buffer").getAsInt();
            if (bufferIndex < 0 || bufferIndex >= buffers.size()) return null;
            var data = ByteBuffer.wrap(buffers.get(bufferIndex)).order(ByteOrder.LITTLE_ENDIAN);
            int offset = view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0;
            int length = view.has("byteLength") ? view.get("byteLength").getAsInt() : -1;
            long limit = (long) offset + length;
            if (offset < 0 || length < 0 || limit > data.limit()) return null;
            int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : 0;
            return new View(data, offset, stride, (int) limit);
        }

        private static float readComponent(ByteBuffer data, int at, int componentType, boolean normalized) {
            return switch (componentType) {
                case 5120 -> normalized ? Math.max(data.get(at) / 127f, -1f) : data.get(at);
                case 5121 -> normalized ? (data.get(at) & 0xFF) / 255f : (data.get(at) & 0xFF);
                case 5122 -> normalized ? Math.max(data.getShort(at) / 32767f, -1f) : data.getShort(at);
                case 5123 -> normalized ? (data.getShort(at) & 0xFFFF) / 65535f : (data.getShort(at) & 0xFFFF);
                case 5125 -> data.getInt(at);
                case 5126 -> data.getFloat(at);
                default -> 0f;
            };
        }

        private static int componentSize(int componentType) {
            return switch (componentType) {
                case 5120, 5121 -> 1;
                case 5122, 5123 -> 2;
                case 5125, 5126 -> 4;
                default -> 0;
            };
        }

        private static int componentsOf(String type) {
            return switch (type) {
                case "SCALAR" -> 1;
                case "VEC2" -> 2;
                case "VEC3" -> 3;
                case "VEC4" -> 4;
                case "MAT4" -> 16;
                default -> -1;
            };
        }

        private JsonArray array(String name) {
            JsonElement element = root.get(name);
            return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
        }
    }
}
