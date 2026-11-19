package com.lowdragmc.photon.client.gameobject.emitter.data.material.kila;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.MaterialContext;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

/**
 * How the particle takes light: a mesh or sphere normal, bumped by a height or normal map, under a key light or six-way
 * lightmaps (Unity's layout: the main texture holds right, top, back; {@link #sixWayNegative} left, bottom, front).
 */
@OnlyIn(Dist.CLIENT)
public class KilaShading extends KilaModule {
    /** ⚠️ ordinals MIRRORED IN {@code kila.fsh} (KilaShadingOpts.x). */
    public enum Normal {
        MESH, SPHERE
    }

    /** ⚠️ ordinals MIRRORED IN {@code kila.fsh} (KilaShadingOpts.y). */
    public enum Bump {
        NONE, HEIGHT, NORMAL_MAP
    }

    /** ⚠️ ordinals MIRRORED IN {@code kila.fsh} (KilaShadingOpts.z). */
    public enum Model {
        LAMBERT, SIX_WAY
    }

    @Persisted
    public Normal normal = Normal.SPHERE;
    /** 0 is the flat quad, 1 a full half sphere */
    @Persisted
    public float bulge = 1;
    @Persisted
    public Bump bump = Bump.NONE;
    /** height: tenths of a block; normal map: how far it tilts */
    @Persisted
    public float bumpStrength = 1;
    /** DirectX-style normal maps have green pointing down */
    @Persisted
    public boolean flipGreen;
    @Persisted(subPersisted = true)
    public final KilaTexture height = new KilaTexture(KilaTextures.NOISE_CLOUD).noise(KilaTexture.Noise.GRADIENT, 8);
    @Persisted(subPersisted = true)
    public final KilaTexture normalMap = new KilaTexture(KilaTextures.NORMAL_NOISE);
    @Persisted
    public Model model = Model.LAMBERT;
    @Persisted(subPersisted = true)
    public final KilaTexture sixWayNegative = new KilaTexture(KilaTextures.SMOKE6_NEG);

    public Normal effectiveNormal() {
        return normal == null ? Normal.SPHERE : normal;
    }

    public Bump effectiveBump() {
        return bump == null ? Bump.NONE : bump;
    }

    public Model effectiveModel() {
        return model == null ? Model.LAMBERT : model;
    }

    public HDRColor effectiveLightColor() {
        return lightColor == null ? HDRColor.white() : lightColor;
    }

    public Vector3f effectiveLightDirection() {
        return lightDirection == null ? new Vector3f(0.5f, 1f, 0.35f) : lightDirection;
    }
    /** in the world, towards the light */
    @Persisted
    public Vector3f lightDirection = new Vector3f(0.5f, 1f, 0.35f);
    @Persisted
    public HDRColor lightColor = HDRColor.white();
    /** how much light reaches the side turned away */
    @Persisted
    public float ambient = 0.35f;
    /** above 0 the light wraps further round the dark side */
    @Persisted
    public float wrap = 0.3f;

    private final Vector3f viewLight = new Vector3f();

    /** A zero or non-finite light vector has no Lambert direction; use the authored default instead. */
    static Vector3f safeLightDirection(Vector3f direction) {
        var safe = direction == null ? new Vector3f(0.5f, 1f, 0.35f)
                : new Vector3f(finite(direction.x, 0.5f, -1f, 1f), finite(direction.y, 1f, -1f, 1f),
                        finite(direction.z, 0.35f, -1f, 1f));
        float lengthSquared = safe.lengthSquared();
        if (!(lengthSquared > 1e-8f && Float.isFinite(lengthSquared))) safe.set(0.5f, 1f, 0.35f);
        return safe.normalize();
    }

    @Override
    public String id() {
        return "shading";
    }

    @Override
    public Category category() {
        return Category.LIGHTING;
    }

    @Override
    public long structure() {
        return effectiveBump().ordinal() | (effectiveModel() == Model.SIX_WAY ? 4 : 0);
    }

    @Override
    public void declare(KilaLayout.Builder layout) {
        layout.define("KILA_SHADING").ivec4("KilaShadingOpts").vec4("KilaShadingParams", 1, 1, 0.3f, 0.35f)
                .vec4("KilaKeyLight", 0, 1, 0, 0).vec4("KilaKeyColor", 1, 1, 1, 1);
        if (effectiveBump() == Bump.HEIGHT) {
            layout.define("KILA_BUMP").define("KILA_BUMP_HEIGHT").fragmentUvChain("KilaBump")
                    .source("BUMP", "KilaBump", height, true);
        } else if (effectiveBump() == Bump.NORMAL_MAP) {
            layout.define("KILA_BUMP").fragmentUvChain("KilaBump").texture("BUMP", normalMap);
        }
        if (effectiveModel() == Model.SIX_WAY) layout.define("KILA_SIXWAY").texture("SIXWAY", sixWayNegative);
    }

    @Override
    public void apply(ShaderInstance shader, MaterialContext context, float[] scratch) {
        shader.safeGetUniform("KilaShadingOpts").set(effectiveNormal().ordinal(), effectiveBump().ordinal(), effectiveModel().ordinal(), flipGreen ? 1 : 0);
        shader.safeGetUniform("KilaShadingParams").set(
                finite(bulge, 1f, 0f, 1f), finite(bumpStrength, 1f, 0f, 16f),
                finite(wrap, 0.3f, 0f, 1f), finite(ambient, 0.35f, 0f, 1f));
        // the shader's normals are in view space
        var safeDirection = safeLightDirection(effectiveLightDirection());
        RenderSystem.getModelViewMatrix().transformDirection(safeDirection, viewLight);
        viewLight.set(safeLightDirection(viewLight));
        shader.safeGetUniform("KilaKeyLight").set(viewLight.x, viewLight.y, viewLight.z, 0f);
        var key = effectiveLightColor().toVector4fOpaque();
        shader.safeGetUniform("KilaKeyColor").set(
                KilaMaterial.finite(key.x), KilaMaterial.finite(key.y), KilaMaterial.finite(key.z), 1f);
        if (effectiveBump() == Bump.HEIGHT) {
            height.uploadUv(shader, "KilaBump");
            shader.safeGetUniform("KilaBumpOpts").set(height.flags(), 0, 0, 0);
            height.uploadSource(shader, "KilaBump", scratch);
        } else if (effectiveBump() == Bump.NORMAL_MAP) {
            normalMap.uploadUv(shader, "KilaBump");
            shader.safeGetUniform("KilaBumpOpts").set(normalMap.flags(), 0, 0, 0);
        }
    }

    @Override
    public void textures(java.util.function.Consumer<KilaTexture> out) {
        if (effectiveBump() == Bump.HEIGHT) out.accept(height);
        if (effectiveBump() == Bump.NORMAL_MAP) out.accept(normalMap);
        if (effectiveModel() == Model.SIX_WAY) out.accept(sixWayNegative);
    }

    @Override
    public void buildModuleConfigurator(ConfiguratorGroup group) {
        KilaUI.choice(group, "kila.shading.normal", Normal.values(), this::effectiveNormal, v -> normal = v);
        KilaUI.showWhen(group, KilaUI.number(group, "kila.shading.bulge", () -> bulge, v -> bulge = v, 1, 0, 1),
                () -> effectiveNormal() == Normal.SPHERE);
        KilaUI.choice(group, "kila.shading.bump", Bump.values(), this::effectiveBump, v -> bump = v);
        KilaUI.showWhen(group, KilaUI.number(group, "kila.shading.bump_strength", () -> bumpStrength,
                v -> bumpStrength = v, 1, 0, 16), () -> effectiveBump() != Bump.NONE);
        var heightGroup = KilaUI.subGroup(group, "kila.shading.height", false);
        KilaUI.texture(heightGroup, "kila.shading.height_texture", height, KilaUI.TextureKind.SCALAR, true, true);
        KilaUI.showWhen(group, heightGroup, () -> effectiveBump() == Bump.HEIGHT);
        var mapGroup = KilaUI.subGroup(group, "kila.shading.normal_map", false);
        KilaUI.texture(mapGroup, "kila.shading.normal_map_texture", normalMap, KilaUI.TextureKind.VECTOR, true);
        KilaUI.bool(mapGroup, "kila.shading.flip_green", () -> flipGreen, v -> flipGreen = v);
        KilaUI.showWhen(group, mapGroup, () -> effectiveBump() == Bump.NORMAL_MAP);

        KilaUI.choice(group, "kila.shading.model", Model.values(), this::effectiveModel, v -> model = v);
        // read through the main texture's uv and flipbook: no chain of its own
        KilaUI.showWhen(group, KilaUI.add(group, new KilaTextureField("kila.shading.six_way_negative", sixWayNegative,
                KilaUI.TextureKind.COLOR, false), "kila.shading.six_way_negative"), () -> effectiveModel() == Model.SIX_WAY);
        KilaUI.vec3(group, "kila.shading.light_direction", this::effectiveLightDirection, v -> lightDirection = v);
        KilaUI.hdr(group, "kila.shading.light_color", this::effectiveLightColor, v -> lightColor = v, false);
        KilaUI.number(group, "kila.shading.ambient", () -> ambient, v -> ambient = v, 0.35f, 0, 1);
        KilaUI.showWhen(group, KilaUI.number(group, "kila.shading.wrap", () -> wrap, v -> wrap = v, 0.3f, 0, 1),
                () -> effectiveModel() == Model.LAMBERT);
    }
}
