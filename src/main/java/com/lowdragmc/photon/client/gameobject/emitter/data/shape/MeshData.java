package com.lowdragmc.photon.client.gameobject.emitter.data.shape;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorSelectorConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.IModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.JsonModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.PhotonMesh;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.vfyjxf.taffy.style.AlignItems;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.lowdragmc.photon.util.RegistryAwareNBTSerializable;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MeshData implements RegistryAwareNBTSerializable<CompoundTag>, IConfigurable, IPersistedSerializable {
    @Getter
    @Persisted
    private IModelSource source = new JsonModelSource();
    // runtime: sampling geometry derived from the source's mesh. derivedFrom tracks which
    // PhotonMesh instance the lists were built from — the shared cache hands out a fresh instance
    // after any invalidation (reload listener, reload button, file polling), so an identity
    // compare is all the staleness detection needed.
    @Nullable
    private volatile PhotonMesh derivedFrom = null;
    private final List<Vector3f> vertices = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<Triangle> triangles = new ArrayList<>();
    @Getter
    private double edgeSumLength;
    @Getter
    private double triangleSumArea;

    public MeshData() {
    }

    public MeshData(CompoundTag nbt) {
        deserializeNBT(Platform.getFrozenRegistry(), nbt);
    }

    public MeshData(ResourceLocation modelLocation) {
        this(new JsonModelSource(modelLocation));
    }

    public MeshData(IModelSource source) {
        this.source = source;
    }

    public void setSource(IModelSource source) {
        this.source = source;
        clearDerived();
    }

    private synchronized void clearDerived() {
        derivedFrom = null;
        vertices.clear();
        edges.clear();
        triangles.clear();
        edgeSumLength = 0;
        triangleSumArea = 0;
    }

    private void ensureLoaded() {
        var mesh = source.getMesh();
        if (mesh == derivedFrom) return;
        synchronized (this) {
            if (mesh == derivedFrom) return; // rebuilt by a parallel-sim worker meanwhile
            if (derivedFrom != null && mesh.topology() == derivedFrom.topology()) {
                refreshPositions(mesh);
                derivedFrom = mesh;
                return;
            }
            vertices.clear();
            edges.clear();
            triangles.clear();
            rebuildFrom(mesh);
            derivedFrom = mesh;
        }
    }

    private void rebuildFrom(PhotonMesh mesh) {
        double sumLength = 0;
        double sumArea = 0;
        var data = mesh.vertices();
        var topology = mesh.samplingTopology();
        for (int vertex = 0; vertex < topology.vertexCount(); vertex++) {
            int corner = topology.firstCorner(vertex);
            int off = PhotonMesh.vertexOffset(corner / 4, corner % 4);
            vertices.add(new Vector3f(data[off], data[off + 1], data[off + 2]));
        }
        Set<Long> seenEdges = new HashSet<>();
        for (int triangle = 0; triangle < topology.triangleCount(); triangle++) {
            int a = topology.triangleVertex(triangle, 0);
            int b = topology.triangleVertex(triangle, 1);
            int c = topology.triangleVertex(triangle, 2);
            sumLength += addEdgeOnce(seenEdges, a, b);
            sumLength += addEdgeOnce(seenEdges, b, c);
            sumLength += addEdgeOnce(seenEdges, c, a);
            sumArea += addTriangle(vertices.get(a), vertices.get(b), vertices.get(c));
        }
        this.edgeSumLength = sumLength;
        this.triangleSumArea = sumArea;
    }

    /** Same topology, new animated positions; retain rest-pose edge/area weights. */
    private void refreshPositions(PhotonMesh mesh) {
        var data = mesh.vertices();
        var topology = mesh.samplingTopology();
        for (int vertex = 0; vertex < topology.vertexCount(); vertex++) {
            int corner = topology.firstCorner(vertex);
            int off = PhotonMesh.vertexOffset(corner / 4, corner % 4);
            vertices.get(vertex).set(data[off], data[off + 1], data[off + 2]);
        }
    }

    /** Add each topological edge only once even when adjacent triangles share its endpoints. */
    private double addEdgeOnce(Set<Long> seen, int a, int b) {
        long key = a < b ? ((long) a << 32) | (b & 0xffffffffL)
                : ((long) b << 32) | (a & 0xffffffffL);
        return seen.add(key) ? addEdge(vertices.get(a), vertices.get(b)) : 0d;
    }

    public List<Vector3f> getVertices() {
        ensureLoaded();
        return vertices;
    }

    public List<Edge> getEdges() {
        ensureLoaded();
        return edges;
    }

    public List<Triangle> getTriangles() {
        ensureLoaded();
        return triangles;
    }

    @Nullable
    public Vector3f getRandomVertex(float t) {
        ensureLoaded();
        if (vertices.isEmpty()) return null;
        return vertices.get((int) (vertices.size() * t));
    }

    @Nullable
    public Edge getRandomEdge(float t) {
        ensureLoaded();
        if (edges.isEmpty()) return null;
        var l = t * edgeSumLength;
        var cl = 0d;
        for (Edge edge : edges) {
            if (l <= edge.length + cl) {
                return edge;
            }
            cl += edge.length;
        }
        return edges.get(edges.size() - 1);
    }

    @Nullable
    public Triangle getRandomTriangle(float t) {
        ensureLoaded();
        if (triangles.isEmpty()) return null;
        var a = t * triangleSumArea;
        var ca = 0d;
        for (var triangle : triangles) {
            if (a <= triangle.area + ca) {
                return triangle;
            }
            ca += triangle.area;
        }
        return triangles.get(triangles.size() - 1);
    }

    private double addEdge(Vector3f a, Vector3f b) {
        var ab = new Edge(a, b);
        if (ab.length > 0) {
            edges.add(ab);
        }
        return ab.length;
    }

    private double addTriangle(Vector3f a, Vector3f b, Vector3f c) {
        var abc = new Triangle(a, b, c);
        if (abc.area > 0) {
            triangles.add(abc);
        }
        return abc.area;
    }

    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider provider, @NotNull CompoundTag nbt) {
        IPersistedSerializable.super.deserializeNBT(provider, nbt);
        if (!nbt.contains("source")) {
            // legacy (pre-v5) payloads store a bare json model id; editor resource files and pasted
            // NBT bypass the project datafixer, so keep these in-place fallbacks
            if (nbt.contains("modelLocation", Tag.TAG_STRING)) {
                source = new JsonModelSource(ResourceLocation.parse(nbt.getString("modelLocation")));
            } else if (nbt.contains("type", Tag.TAG_STRING)) {
                // a bare IModelSource wrapper {type, data} (renderer payloads before MeshData wrapping)
                source = IModelSource.deserializeWrapper(nbt);
            }
        }
        clearDerived();
    }

    @OnlyIn(Dist.CLIENT)
    public Scene createPreviewScene() {
        return createInspectorPreview();
    }

    /** The editable preview shown in this mesh's inspector. */
    @OnlyIn(Dist.CLIENT)
    public Scene createInspectorPreview() {
        var scene = buildPreview(null);
        scene.layout(layout -> {
            layout.setAspectRatio(1.0f);
            layout.widthPercent(80);
            layout.alignSelf(AlignItems.CENTER);
            layout.paddingAll(3);
        });
        scene.style(style -> style.backgroundTexture(Sprites.BORDER1_RT1));
        scene.moveInlineAsDefault();
        scene.addClass("preview_bg");
        return scene;
    }

    /**
     * A low-resolution, non-interactive preview for resource-browser tiles. Keeping each thumbnail
     * at 128px bounds render-target memory while leaving camera orbit/zoom available in the inspector.
     */
    @OnlyIn(Dist.CLIENT)
    public Scene createTilePreview() {
        return buildPreview(Size.of(128, 128)).setIntractable(false);
    }

    @OnlyIn(Dist.CLIENT)
    private Scene buildPreview(@Nullable Size fboSize) {
        var level = new TrackedDummyWorld();
        level.addBlock(BlockPos.ZERO, BlockInfo.fromBlock(Blocks.AIR));
        var scene = new Scene();
        scene.setRenderFacing(false);
        scene.setRenderSelect(false);
        scene.setTickWorld(false);
        scene.createScene(level, fboSize != null, fboSize);
        var renderer = scene.getRenderer();
        assert renderer != null;
        renderer.setOnLookingAt(null);
        if (fboSize != null) renderer.setFov(40);
        scene.setRenderedCore(Collections.singleton(BlockPos.ZERO), null);
        var framedMesh = new PhotonMesh[]{null};
        scene.setBeforeWorldRender(s -> {
            var mesh = source.getMesh();
            if (mesh == framedMesh[0]) return;
            framedMesh[0] = mesh;
            frame(s);
        });
        scene.setAfterWorldRender(s -> drawLineFrames(new PoseStack()));
        return scene;
    }

    /** Keep the mesh framed after a source switch, import, or resource reload. */
    @OnlyIn(Dist.CLIENT)
    private void frame(Scene scene) {
        var min = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        var max = new Vector3f(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        var meshVertices = getVertices();
        if (meshVertices.isEmpty()) {
            min.set(0, 0, 0);
            max.set(1, 1, 1);
        } else {
            for (var vertex : meshVertices) {
                min.min(vertex);
                max.max(vertex);
            }
        }
        var center = new Vector3f((min.x + max.x) / 2f + 0.5f,
                (min.y + max.y) / 2f + 0.5f, (min.z + max.z) / 2f + 0.5f);
        var extent = Math.max(Math.max(max.x - min.x + 1, max.y - min.y + 1), max.z - min.z + 1);
        var zoom = (float) (3.5 * Math.sqrt(Math.max(extent, 1)));
        scene.getRenderer().setCameraLookAt(center, zoom, Math.toRadians(-135), Math.toRadians(25));
    }

    @OnlyIn(Dist.CLIENT)
    public void drawLineFrames(PoseStack poseStack) {
        var edges = getEdges();
        if (edges.isEmpty()) return;
        var tessellator = Tesselator.getInstance();
        var pose = poseStack.last();
        var mat = pose.pose();

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        var buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        RenderSystem.lineWidth(10);

        for (var edge : edges) {
            var a = edge.a;
            var b = edge.b;
            float f = b.x - a.x;
            float f1 = b.y - a.y;
            float f2 = b.z - a.z;
            float f3 = Mth.sqrt(f * f + f1 * f1 + f2 * f2);
            f /= f3;
            f1 /= f3;
            f2 /= f3;

            // +0.5: mesh space is centered, the preview block spans 0..1 (origin sits at block center)
            buffer.vertex(mat, a.x + 0.5f, a.y + 0.5f, a.z + 0.5f).color(-1)
                    .normal(poseStack.last().normal(), f, f1, f2).endVertex();
            buffer.vertex(mat, b.x + 0.5f, b.y + 0.5f, b.z + 0.5f).color(-1)
                    .normal(poseStack.last().normal(), f, f1, f2).endVertex();
        }

        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void buildConfigurator(ConfiguratorGroup father) {
        father.addConfigurators(new Configurator("ldlib.gui.editor.group.preview").addChild(createPreviewScene()));
        father.addConfigurator(new ConfiguratorSelectorConfigurator<>(
                "photon.model_source",
                () -> source.name(),
                name -> setSource(PhotonRegistries.MODEL_SOURCES.get(name).value().get()),
                "json_model",
                true,
                // resource_mesh is a live reference, not a first-class geometry source — it exists only
                // for drag/dialog picks, so a resource shouldn't be able to reference another resource
                PhotonRegistries.MODEL_SOURCES.keys().stream().filter(k -> !k.equals("resource_mesh")).toList(),
                s -> "photon.model_source." + s,
                (name, group) -> source.buildConfigurator(group)));
    }

    public static class Edge {

        public final Vector3f a, b;

        public final double length;

        public Edge(Vector3f a, Vector3f b) {
            this.a = a;
            this.b = b;
            length = new Vector3f(a).sub(b).length();
        }
    }

    public static class Triangle {

        public final Vector3f a, b, c;

        public final double area;

        public Triangle(Vector3f a, Vector3f b, Vector3f c) {
            this.a = a;
            this.b = b;
            this.c = c;
            var nx = (b.y - a.y) * (c.z - a.z) - (b.z - a.z) * (c.y - a.y);
            var ny = (b.z - a.z) * (c.x - a.x) - (b.x - a.x) * (c.z - a.z);
            var nz = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
            area = 0.5 * Math.sqrt(nx * nx + ny * ny + nz * nz);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MeshData meshData = (MeshData) o;
        return source.equals(meshData.source);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }
}
