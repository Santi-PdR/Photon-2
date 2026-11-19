package com.lowdragmc.photon.client.gameobject.emitter.data.material.kila;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.MaterialContext;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import java.util.function.BiConsumer;

/** Pushes vertices by a scrolling noise, along the normal or a fixed axis. */
@OnlyIn(Dist.CLIENT)
public class KilaVertexOffset extends KilaModule {
    public enum Direction {
        NORMAL, AXIS
    }

    @Persisted(subPersisted = true)
    public final KilaTexture texture = new KilaTexture(KilaTextures.NOISE_PERLIN).scroll(0.1f, 0f);
    @Persisted(subPersisted = true)
    public final KilaDriver strength = new KilaDriver(0.1f);
    @Persisted
    public Direction direction = Direction.NORMAL;
    @Persisted
    public Vector3f axis = new Vector3f(0, 1, 0);
    @Persisted
    public boolean bidirectional;
    /** fades the push to nothing towards v = 0 and v = 1, which keeps a sphere's poles from tearing */
    @Persisted
    public float edgeFade;

    public Direction effectiveDirection() {
        return direction == null ? Direction.NORMAL : direction;
    }

    public Vector3f effectiveAxis() {
        return axis == null ? new Vector3f(0, 1, 0)
                : new Vector3f(finite(axis.x, 0f, -10_000f, 10_000f),
                        finite(axis.y, 1f, -10_000f, 10_000f),
                        finite(axis.z, 0f, -10_000f, 10_000f));
    }

    @Override
    public String id() {
        return "vertex_offset";
    }

    @Override
    public Category category() {
        return Category.VERTEX;
    }

    @Override
    public void declare(KilaLayout.Builder layout) {
        layout.define("KILA_VERTEX_OFFSET").uvChain("KilaVertex").vec4("KilaVertexDir").vec4("KilaVertexShape")
                .source("VERTEX", "KilaVertex", texture, true)
                .params(KilaParam.VERTEX_OFFSET_STRENGTH);
    }

    @Override
    public void apply(ShaderInstance shader, MaterialContext context, float[] scratch) {
        texture.uploadUv(shader, "KilaVertex");
        shader.safeGetUniform("KilaVertexOpts").set(texture.flags(), 0, 0, 0);
        texture.uploadSource(shader, "KilaVertex", scratch);
        var safeAxis = effectiveAxis();
        shader.safeGetUniform("KilaVertexDir").set(effectiveDirection() == Direction.AXIS ? 1f : 0f,
                safeAxis.x, safeAxis.y, safeAxis.z);
        shader.safeGetUniform("KilaVertexShape").set(
                bidirectional ? 1f : 0f, finite(edgeFade, 0f, 0f, 0.5f), 0f, 0f);
    }

    @Override
    public void textures(java.util.function.Consumer<KilaTexture> out) {
        out.accept(texture);
    }

    @Override
    public void drivers(BiConsumer<KilaParam, KilaDriver> out) {
        out.accept(KilaParam.VERTEX_OFFSET_STRENGTH, strength);
    }

    @Override
    public void buildModuleConfigurator(ConfiguratorGroup group) {
        KilaUI.driver(group, "kila.vertex_offset.strength", strength, -4, 4);
        KilaUI.texture(group, "kila.vertex_offset.texture", texture, KilaUI.TextureKind.SCALAR, true, true);
        KilaUI.choice(group, "kila.vertex_offset.direction", Direction.values(), () -> direction, v -> direction = v);
        KilaUI.showWhen(group, KilaUI.vec3(group, "kila.vertex_offset.axis", this::effectiveAxis, v -> axis = v),
                () -> effectiveDirection() == Direction.AXIS);
        KilaUI.bool(group, "kila.vertex_offset.bidirectional", () -> bidirectional, v -> bidirectional = v);
        KilaUI.number(group, "kila.vertex_offset.edge_fade", () -> edgeFade, v -> edgeFade = v, 0, 0, 0.5f);
    }
}
