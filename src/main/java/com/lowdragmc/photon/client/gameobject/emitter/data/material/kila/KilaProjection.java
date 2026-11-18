package com.lowdragmc.photon.client.gameobject.emitter.data.material.kila;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.MaterialContext;
import com.lowdragmc.photon.client.shadergraph.PhotonShaderCompiler;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Paints the particle onto the scene behind it, inside a box its quad sets. The quad must cover the decal on screen
 * (a billboard twice the decal's size does), and the material draws it without a depth test.
 */
@OnlyIn(Dist.CLIENT)
public class KilaProjection extends KilaModule {
    /** ⚠️ ordinals MIRRORED IN {@code kila.fsh} (KilaProjectionShape.x). */
    public enum Orientation {
        /** flat on the ground under the particle's centre, whichever way the quad faces */
        GROUND,
        /** the quad's own plane: for a quad laid on a surface and seen from the front */
        QUAD
    }

    @Persisted
    public Orientation orientation = Orientation.GROUND;
    /** the decal's width as a share of the quad's */
    @Persisted
    public float size = 0.5f;
    /** blocks up and down from the particle's centre */
    @Persisted
    public float depth = 1.5f;
    /** 0..1 of the depth where it starts fading out */
    @Persisted
    public float fade = 0.6f;
    /** only surfaces square to the box's up */
    @Persisted
    public boolean facingOnly = true;

    public Orientation effectiveOrientation() {
        return orientation == null ? Orientation.GROUND : orientation;
    }

    @Override
    public String id() {
        return "projection";
    }

    @Override
    public Category category() {
        return Category.TEXTURE;
    }

    @Override
    public void declare(KilaLayout.Builder layout) {
        layout.define("KILA_PROJECTION").define("KILA_REL_POS").vec4("KilaProjection", 1.5f, 0.6f, 1, 0)
                .vec4("KilaProjectionShape", 0, 0.5f, 0, 0).mat4("U_InverseProjectionMatrix")
                .sampler(PhotonShaderCompiler.SCENE_DEPTH);
    }

    @Override
    public void apply(ShaderInstance shader, MaterialContext context, float[] scratch) {
        boolean bound = KilaDepth.bindSceneDepth(shader, context);
        shader.safeGetUniform("KilaProjection").set(
                finite(depth, 1.5f, 0.01f, 64f), finite(fade, 0.6f, 0f, 1f),
                facingOnly ? 1f : 0f, bound ? 1f : 0f);
        shader.safeGetUniform("KilaProjectionShape").set(
                (float) effectiveOrientation().ordinal(), finite(size, 0.5f, 0.01f, 1f), 0f, 0f);
    }

    @Override
    public void buildModuleConfigurator(ConfiguratorGroup group) {
        KilaUI.choice(group, "kila.projection.orientation", Orientation.values(), this::effectiveOrientation, v -> orientation = v);
        KilaUI.number(group, "kila.projection.size", () -> size, v -> size = v, 0.5f, 0.01f, 1);
        KilaUI.number(group, "kila.projection.depth", () -> depth, v -> depth = v, 1.5f, 0.01f, 64);
        KilaUI.number(group, "kila.projection.fade", () -> fade, v -> fade = v, 0.6f, 0, 1);
        KilaUI.bool(group, "kila.projection.facing_only", () -> facingOnly, v -> facingOnly = v);
    }
}
