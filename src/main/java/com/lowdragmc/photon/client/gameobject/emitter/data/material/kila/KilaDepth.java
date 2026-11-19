package com.lowdragmc.photon.client.gameobject.emitter.data.material.kila;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.MaterialContext;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.SoftParticles;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;
import com.lowdragmc.photon.client.shadergraph.PhotonShaderCompiler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.function.BiConsumer;

/**
 * Fades softly into the opaque scene behind the particle and glows where it touches. Binding goes through
 * {@link SoftParticles#apply}, so the scene depth copy stays pull-based.
 */
@OnlyIn(Dist.CLIENT)
public class KilaDepth extends KilaModule {
    /** ⚠️ ordinals MIRRORED IN {@code kila.fsh} (KilaDepthOpts.z). */
    public enum ContactMode {
        ADD, REPLACE
    }

    /** ⚠️ ordinals MIRRORED IN {@code kila.fsh} (KilaDepthOpts.y). */
    public enum Measure {
        /** along the view ray: a band seen at a grazing angle looks thinner */
        VIEW,
        /** square to the surface behind, where that is a flat face: the same width from any angle */
        SURFACE
    }

    @Persisted
    public boolean soft = true;
    @Persisted
    public float distance = 1;
    @Persisted
    public float power = 1;

    /** blocks; 0 = no glow */
    @Persisted(subPersisted = true)
    public final KilaDriver contactWidth = new KilaDriver(0);
    @Persisted(subPersisted = true)
    public final KilaDriver contactStrength = new KilaDriver(1);
    @Persisted
    public HDRColor contactColor = new HDRColor(0.6f, 0.9f, 1f, 1f, 2f);
    @Persisted
    public ContactMode contactMode = ContactMode.ADD;
    /** 1 = linear; above 1 the glow hugs the surface */
    @Persisted
    public float contactPower = 1;
    /** 0 fades across the whole band; towards 1 it stays solid to its edge */
    @Persisted
    public float contactHardness;
    /** the glow is cut by the particle's own shape; off, it fills the quad wherever it touches */
    @Persisted
    public boolean contactFollowsShape = true;
    @Persisted
    public Measure measure = Measure.VIEW;
    @Persisted
    public boolean contactGradient;
    /** left = at the surface, right = the band's outer edge */
    @Persisted(subPersisted = true)
    public final GradientColor contactGradientColors = new GradientColor(0xFFFFFFFF, 0xFF7FE6FF, 0xFF2A6CFF, 0x002A6CFF);
    /** bands per width, running outwards; 0 = none */
    @Persisted
    public int contactPulses;
    /** bands per second */
    @Persisted
    public float contactPulseSpeed = 1;
    /** how much a noise eats into the band from its outer edge */
    @Persisted
    public float contactBreakup;
    @Persisted(subPersisted = true)
    public final KilaTexture contactNoise = new KilaTexture(KilaTextures.NOISE_CLOUD).noise(KilaTexture.Noise.GRADIENT, 6)
            .scroll(0, 0.3f);

    public ContactMode effectiveContactMode() {
        return contactMode == null ? ContactMode.ADD : contactMode;
    }

    public Measure effectiveMeasure() {
        return measure == null ? Measure.VIEW : measure;
    }

    public HDRColor effectiveContactColor() {
        return contactColor == null ? new HDRColor(0.6f, 0.9f, 1f, 1f, 2f) : contactColor;
    }

    private static final Matrix4f INVERSE_PROJECTION = new Matrix4f();

    private final SoftParticles softParticles = new SoftParticles();
    private final KilaBakedGradient bakedGradient = new KilaBakedGradient(contactGradientColors);

    @Override
    public String id() {
        return "depth";
    }

    @Override
    public Category category() {
        return Category.ALPHA;
    }

    // The gradient and breakup noise cost samplers: do not reserve them when the width/strength cannot glow.
    private boolean canProducePositive(KilaDriver driver) {
        return driver.safeValue() > 0f || (driver.isDriven() && driver.safeScale() != 0f);
    }

    private boolean contactGlowPossible() {
        return canProducePositive(contactWidth) && canProducePositive(contactStrength);
    }

    private float effectiveContactBreakup() {
        return contactGlowPossible() ? finite(contactBreakup, 0f, 0f, 1f) : 0f;
    }

    private boolean breakup() {
        return effectiveContactBreakup() > 0f;
    }

    private boolean contactGradientEnabled() {
        return contactGlowPossible() && contactGradient;
    }

    @Override
    public long structure() {
        return (effectiveMeasure() == Measure.SURFACE ? 1 : 0) | (contactGradientEnabled() ? 2 : 0) | (breakup() ? 4 : 0);
    }

    @Override
    public void declare(KilaLayout.Builder layout) {
        layout.define("KILA_DEPTH").vec4("SoftParticleParams", 1, 1, 0, 0).mat4("U_InverseProjectionMatrix")
                .ivec4("KilaDepthOpts").vec4("KilaContactColor").vec4("KilaContactShape", 1, 0, 0, 0)
                .vec4("KilaContactPulse").float1("GameTime", 0).sampler(PhotonShaderCompiler.SCENE_DEPTH)
                .params(KilaParam.CONTACT_STRENGTH, KilaParam.CONTACT_WIDTH);
        if (effectiveMeasure() == Measure.SURFACE) layout.define("KILA_REL_POS");
        if (contactGradientEnabled()) layout.define("KILA_CONTACT_RAMP").dynamicTexture("CONTACT_RAMP", bakedGradient::textureId);
        if (breakup()) {
            layout.define("KILA_CONTACT_BREAKUP").fragmentUvChain("KilaContact")
                    .source("CONTACT", "KilaContact", contactNoise, true);
        }
    }

    @Override
    public void apply(ShaderInstance shader, MaterialContext context, float[] scratch) {
        // Depth is also required by the independent contact-glow feature when soft fading is disabled.
        softParticles.setEnable(true);
        softParticles.distance = finite(distance, 1f, 0.001f, 64f);
        softParticles.power = finite(power, 1f, 0.01f, 16f);
        softParticles.apply(shader, context);
        shader.safeGetUniform("KilaDepthOpts").set(soft ? 1 : 0, effectiveMeasure().ordinal(), effectiveContactMode().ordinal(),
                contactFollowsShape ? 1 : 0);
        var glow = effectiveContactColor().toVector4fOpaque();
        shader.safeGetUniform("KilaContactColor").set(
                KilaMaterial.finite(glow.x), KilaMaterial.finite(glow.y), KilaMaterial.finite(glow.z), 0f);
        shader.safeGetUniform("KilaContactShape").set(
                finite(contactPower, 1f, 0.05f, 16f), finite(contactHardness, 0f, 0f, 0.99f),
                effectiveContactBreakup(), (float) net.minecraft.util.Mth.clamp(contactPulses, 0, 32));
        shader.safeGetUniform("KilaContactPulse").set(finite(contactPulseSpeed, 1f, -16f, 16f), 0f, 0f, 0f);
        if (breakup()) {
            contactNoise.uploadUv(shader, "KilaContact");
            shader.safeGetUniform("KilaContactOpts").set(contactNoise.flags(), 0, 0, 0);
            contactNoise.uploadSource(shader, "KilaContact", scratch);
        }
    }

    /** Binds the scene depth for another module (asking is what takes the copy); false when there is none. */
    static boolean bindSceneDepth(ShaderInstance shader, MaterialContext context) {
        int depth = 0;
        if (!context.isRenderingPreview()) {
            var pipeline = RenderPassPipeline.getCurrent();
            if (pipeline != null) depth = pipeline.getSceneSamplers().depthTexture();
        }
        if (depth <= 0) {
            // null is what ShaderInstance.apply skips
            //noinspection DataFlowIssue
            shader.setSampler(PhotonShaderCompiler.SCENE_DEPTH, null);
            return false;
        }
        shader.setSampler(PhotonShaderCompiler.SCENE_DEPTH, depth);
        shader.safeGetUniform("U_InverseProjectionMatrix").set(RenderSystem.getProjectionMatrix().invert(INVERSE_PROJECTION));
        return true;
    }

    @Override
    public void textures(java.util.function.Consumer<KilaTexture> out) {
        if (breakup()) out.accept(contactNoise);
    }

    @Override
    public void drivers(BiConsumer<KilaParam, KilaDriver> out) {
        out.accept(KilaParam.CONTACT_STRENGTH, contactStrength);
        out.accept(KilaParam.CONTACT_WIDTH, contactWidth);
    }

    @Override
    public void buildModuleConfigurator(ConfiguratorGroup group) {
        KilaUI.bool(group, "kila.depth.soft", () -> soft, v -> soft = v);
        KilaUI.showWhen(group, KilaUI.number(group, "kila.depth.distance", () -> distance, v -> distance = v, 1, 0.001f, 64),
                () -> soft);
        KilaUI.showWhen(group, KilaUI.number(group, "kila.depth.power", () -> power, v -> power = v, 1, 0.01f, 16),
                () -> soft);

        var contact = KilaUI.subGroup(group, "kila.depth.contact", false);
        KilaUI.driver(contact, "kila.depth.contact.width", contactWidth, 0, 16);
        KilaUI.driver(contact, "kila.depth.contact.strength", contactStrength, 0, 1);
        KilaUI.hdr(contact, "kila.depth.contact.color", this::effectiveContactColor, v -> contactColor = v, false);
        KilaUI.choice(contact, "kila.depth.contact.mode", ContactMode.values(), this::effectiveContactMode, v -> contactMode = v);
        KilaUI.number(contact, "kila.depth.contact.power", () -> contactPower, v -> contactPower = v, 1, 0.05f, 16);
        KilaUI.number(contact, "kila.depth.contact.hardness", () -> contactHardness, v -> contactHardness = v, 0, 0, 0.99f);
        KilaUI.bool(contact, "kila.depth.contact.follow_shape", () -> contactFollowsShape, v -> contactFollowsShape = v);
        KilaUI.choice(contact, "kila.depth.contact.measure", Measure.values(), this::effectiveMeasure, v -> measure = v);
        KilaUI.bool(contact, "kila.depth.contact.gradient", () -> contactGradient, v -> contactGradient = v);
        KilaUI.showWhen(contact, KilaUI.gradient(contact, "kila.depth.contact.gradient_colors",
                contactGradientColors::copy, v -> KilaBakedGradient.copy(v, contactGradientColors)), () -> contactGradient);

        var pulse = KilaUI.subGroup(contact, "kila.depth.contact.pulse", true);
        KilaUI.integer(pulse, "kila.depth.contact.pulses", () -> contactPulses, v -> contactPulses = v, 0, 0, 32);
        KilaUI.number(pulse, "kila.depth.contact.pulse_speed", () -> contactPulseSpeed, v -> contactPulseSpeed = v,
                1, -16, 16);

        var breakup = KilaUI.subGroup(contact, "kila.depth.contact.breakup_group", true);
        KilaUI.number(breakup, "kila.depth.contact.breakup", () -> contactBreakup, v -> contactBreakup = v, 0, 0, 1);
        KilaUI.texture(breakup, "kila.depth.contact.noise", contactNoise, KilaUI.TextureKind.SCALAR, true, true);
    }
}
