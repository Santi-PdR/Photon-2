package com.lowdragmc.photon.client.gameobject.emitter.data.material.kila;

import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.function.Consumer;

/**
 * Starting points: each is a few modules set up the way that kind of effect is usually built. Between them every
 * module and the main options of each are exercised across the catalogue and editor smoke checks, so the presets double as examples.
 */
@OnlyIn(Dist.CLIENT)
public final class KilaPresets {
    public enum Category {
        BASIC, FIRE_SMOKE, MAGIC, GROUND, SURFACE, SCREEN, STYLIZED;

        public String langKey() {
            return "kila.preset.category." + name().toLowerCase();
        }
    }

    /** What the preset is built to be drawn on. */
    public enum Target {
        PARTICLE, TRAIL, MESH;

        public String langKey() {
            return "kila.preset.target." + name().toLowerCase();
        }
    }

    public record Preset(String id, Category category, Target target, Consumer<KilaMaterial> setup) {
        Preset(String id, Category category, Consumer<KilaMaterial> setup) {
            this(id, category, Target.PARTICLE, setup);
        }

        public String langKey() {
            return "kila.preset." + id;
        }

        public KilaMaterial create() {
            var material = new KilaMaterial();
            setup.accept(material);
            return material;
        }
    }

    public static final Preset GLOW = new Preset("glow", Category.BASIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.color = new HDRColor(1f, 0.5f, 0.15f, 1f, 1.5f);
        m.main.texture.texture = KilaTextures.GLOW;
    });

    /** An anamorphic cross of light: mix into hit flashes and cores. */
    public static final Preset FLARE = new Preset("flare", Category.BASIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.color = new HDRColor(1f, 0.92f, 0.75f, 1f, 2f);
        m.main.texture.texture = KilaTextures.FLARE;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
    });

    public static final Preset SMOKE = new Preset("smoke", Category.FIRE_SMOKE, m -> {
        m.color = new HDRColor(0.85f, 0.85f, 0.88f, 0.9f, 1f);
        m.main.texture.texture = KilaTextures.SOFT_CIRCLE;
        m.layer.setEnable(true);
        m.layer.texture.texture = KilaTextures.NOISE_CLOUD;
        m.layer.texture.scroll(0.04f, 0.06f);
        m.layer.strength.value = 0.7f;
        m.distortion.setEnable(true);
        m.distortion.strength.value = 0.04f;
        m.depth.setEnable(true);
    });

    /** A soft animated puff using the authored motion sheet instead of ghosting between smoke frames. */
    public static final Preset SOFT_PUFF = new Preset("soft_puff", Category.FIRE_SMOKE, m -> {
        m.blend = BlendMode.Preset.PREMULTIPLIED;
        m.color = new HDRColor(0.78f, 0.8f, 0.84f, 0.85f, 1f);
        m.main.texture.texture = KilaTextures.SMOKE_FLIP;
        m.main.flipbook = true;
        m.main.columns = 4;
        m.main.rows = 4;
        m.main.randomRow = true;
        m.main.frameSource = KilaMainTexture.FrameSource.DRIVER;
        m.main.frame.bind(KilaDriver.Source.LIFE, 1);
        m.main.frameBlend = true;
        m.main.motionVectors = true;
        m.main.motionTexture.texture = KilaTextures.SMOKE_FLIP_MV;
        m.depth.setEnable(true);
        m.lit.setEnable(true);
    });

    /** Burns away as the particle's colour-over-lifetime alpha drops — which also works on trails. */
    public static final Preset DISSOLVE = new Preset("dissolve", Category.BASIC, m -> {
        m.vertexColor = KilaMaterial.VertexColor.RGB_ONLY;
        m.main.texture.texture = KilaTextures.SOFT_CIRCLE;
        m.dissolve.setEnable(true);
        m.dissolve.progress.value = 1;
        m.dissolve.progress.bind(KilaDriver.Source.VERTEX_A, -1);
    });

    public static final Preset MAGIC_CIRCLE = new Preset("magic_circle", Category.MAGIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.35f, 0.75f, 1f, 1f, 3f);
        m.main.texture.texture = KilaTextures.RUNE_CIRCLE;
        m.main.texture.rotationSpeed = 30;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.ADD;
        m.layer.texture.texture = KilaTextures.NOISE_CELLS;
        m.layer.texture.polar = true;
        m.layer.texture.scroll(0f, 0.2f);
        m.layer.strength.value = 0.6f;
    });

    /** The magic circle painted onto the ground under a billboard, over steps and slopes. */
    public static final Preset GROUND_CIRCLE = new Preset("ground_circle", Category.GROUND, m -> {
        MAGIC_CIRCLE.setup.accept(m);
        m.projection.setEnable(true);
        m.projection.depth = 2;
    });

    public static final Preset SLASH = new Preset("slash", Category.BASIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(1f, 1f, 1f, 1f, 2.5f);
        m.main.texture.texture = KilaTextures.SLASH;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.ALPHA;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF001A60, 0xFF1060FF, 0xFF50C8FF, 0xFFFFFFFF).getRgbP());
        m.dissolve.setEnable(true);
        m.dissolve.progress.value = 1;
        m.dissolve.progress.bind(KilaDriver.Source.VERTEX_A, -1);
        m.dissolve.direction = KilaDissolve.Direction.U;
        m.dissolve.edgeWidth.value = 0.04f;
        m.vertexColor = KilaMaterial.VertexColor.RGB_ONLY;
    });

    public static final Preset SHOCKWAVE = new Preset("shockwave", Category.BASIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.8f, 0.9f, 1f, 1f, 2f);
        m.main.texture.texture = KilaTextures.RING;
        m.main.texture.wrap(KilaTexture.Wrap.CLAMP, KilaTexture.Wrap.CLAMP);
        m.distortion.setEnable(true);
        m.distortion.texture.polar = true;
        m.distortion.texture.scroll(0f, 0.3f);
        m.distortion.strength.value = 0.03f;
    });

    /** For a sphere or other mesh: a bright rim, a slowly crawling energy layer. */
    public static final Preset ENERGY_SHIELD = new Preset("energy_shield", Category.SURFACE, Target.MESH, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.color = new HDRColor(0.25f, 0.6f, 1f, 0.35f, 1f);
        m.main.texture.texture = KilaTextures.WHITE;
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.ADD;
        m.layer.texture.texture = KilaTextures.NOISE_CELLS;
        m.layer.texture.scroll(0.05f, 0.1f);
        m.fresnel.setEnable(true);
        m.fresnel.power = 3;
        m.fresnel.color = new HDRColor(0.4f, 0.8f, 1f, 1f, 4f);
        m.depth.setEnable(true);
        m.depth.distance = 0.3f;
        m.depth.contactWidth.value = 0.5f;
        // the body is faint: the ring where it meets the ground is not
        m.depth.contactFollowsShape = false;
        m.depth.measure = KilaDepth.Measure.SURFACE;
        m.depth.contactHardness = 0.3f;
        m.depth.contactBreakup = 0.5f;
    });

    public static final Preset FIRE = new Preset("fire", Category.FIRE_SMOKE, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.vertexColor = KilaMaterial.VertexColor.RGB_ONLY;
        m.color = new HDRColor(1f, 1f, 1f, 1f, 1.5f);
        m.main.texture.texture = KilaTextures.GLOW;
        m.distortion.setEnable(true);
        m.distortion.strength.value = 0.12f;
        m.distortion.texture.scroll(0f, 0.6f);
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.ALPHA;
        m.dissolve.setEnable(true);
        m.dissolve.texture.scroll(0f, 0.4f);
        m.dissolve.progress.value = 1;
        m.dissolve.progress.bind(KilaDriver.Source.VERTEX_A, -1);
        m.dissolve.softness = 0.2f;
        m.dissolve.edgeWidth.value = 0;
    });

    /** An electric web: the bright borders of a cell noise, crawling and jittering, fading out at the rim. */
    public static final Preset LIGHTNING = new Preset("lightning", Category.MAGIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.45f, 0.65f, 1f, 1f, 3.5f);
        m.alphaSharpen = 3;
        m.main.texture.texture = KilaTextures.NOISE_CELLS;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.scroll(0.6f, 0.25f);
        m.distortion.setEnable(true);
        m.distortion.strength.value = 0.06f;
        m.distortion.texture.scroll(1.2f, -0.8f);
        m.mask1.setEnable(true);
        // the alpha power sharpens the mask too: this leaves it about linear
        m.mask1.power = 0.35f;
    });

    /** A branching plasma stroke with a soft halo, ready for impact flashes or animated billboard arcs. */
    public static final Preset ARC_BOLT = new Preset("arc_bolt", Category.MAGIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.45f, 0.78f, 1f, 1f, 2.4f);
        m.main.texture.texture = KilaTextures.LIGHTNING_BOLT;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.alphaSharpen = 1.15f;
        m.distortion.setEnable(true);
        m.distortion.texture.noise(KilaTexture.Noise.SIMPLE, 5).scroll(0.2f, 0.8f);
        m.distortion.strength.value = 0.012f;
        m.depth.setEnable(true);
    });

    /** Shimmering air: the scene behind, pushed around by a rising noise. Alpha blend, so the soft edge fades it in. */
    public static final Preset HEAT_HAZE = new Preset("heat_haze", Category.SCREEN, m -> {
        m.worldLight = false;
        m.fog = false;
        m.main.texture.texture = KilaTextures.SOFT_CIRCLE;
        m.screenDistortion.setEnable(true);
        m.screenDistortion.texture.noise(KilaTexture.Noise.GRADIENT, 5).scroll(0f, 0.6f);
        m.screenDistortion.strength.value = 0.015f;
        m.depth.setEnable(true);
    });

    /** A vortex: cloud noise in polar space, twirled into spiral arms and pulled inwards. */
    public static final Preset PORTAL = new Preset("portal", Category.MAGIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(1f, 1f, 1f, 1f, 2.5f);
        m.main.texture.texture = KilaTextures.NOISE_CLOUD;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.polar = true;
        m.main.texture.polarAngular = 3;
        m.main.texture.scroll(0.35f, 0.05f);
        m.uvEffects.setEnable(true);
        m.uvEffects.twirl.value = 7;
        m.mask1.setEnable(true);
        m.mask1.power = 0.6f;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.ALPHA;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF12002E, 0xFF5B12D8, 0xFFB46CFF, 0xFFFFFFFF).getRgbP());
    });

    /** The swirl flow map as colour, spinning, cut to a disc by Mask 1. */
    public static final Preset HUE_SWIRL = new Preset("hue_swirl", Category.MAGIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(1f, 1f, 1f, 1f, 1.8f);
        m.main.texture.texture = KilaTextures.FLOW_SWIRL;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.main.texture.rotationSpeed = 40;
        m.mask1.setEnable(true);
        m.mask1.power = 0.7f;
    });

    /** Wispy spiral arms spinning in place, tinted purple by a Color Ramp. */
    public static final Preset SPIRAL = new Preset("spiral", Category.MAGIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(1f, 1f, 1f, 1f, 2.6f);
        m.main.texture.texture = KilaTextures.SPIRAL;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.main.texture.rotationSpeed = 35;
        m.mask1.setEnable(true);
        m.mask1.power = 0.55f;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.ALPHA;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF1A0038, 0xFF6A18E8, 0xFFC888FF, 0xFFFFFFFF).getRgbP());
    });

    /** Filaments spinning around a dark core. */
    public static final Preset BLACK_HOLE = new Preset("black_hole", Category.MAGIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(1f, 1f, 1f, 1f, 2.2f);
        m.main.texture.texture = KilaTextures.VORTEX;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.main.texture.rotationSpeed = 22;
        m.mask1.setEnable(true);
        m.mask1.power = 0.5f;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.ALPHA;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF080018, 0xFF3A0A80, 0xFF9B4CFF, 0xFFFFFFFF).getRgbP());
    });

    /** Soft radial rays turning slowly. */
    public static final Preset STAR_BURST = new Preset("star_burst", Category.MAGIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(1f, 0.85f, 0.5f, 1f, 2.2f);
        m.main.texture.texture = KilaTextures.STAR_RAYS;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.main.texture.rotationSpeed = 12;
        m.mask1.setEnable(true);
        m.mask1.power = 0.45f;
    });

    /** A glowing hex spinning in place, a crawling polar noise on top. */
    public static final Preset HEXAGON = new Preset("hexagon", Category.MAGIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.45f, 0.85f, 1f, 1f, 2.4f);
        m.main.texture.texture = KilaTextures.HEXAGON;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.main.texture.rotationSpeed = 18;
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.ADD;
        m.layer.texture.texture = KilaTextures.NOISE_CELLS;
        m.layer.texture.polar = true;
        m.layer.texture.scroll(0f, 0.25f);
        m.layer.strength.value = 0.45f;
        m.mask1.setEnable(true);
        m.mask1.power = 0.5f;
    });

    /** Concentric rings, a polar UV Distortion breathing them. */
    public static final Preset RIPPLE = new Preset("ripple", Category.BASIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.7f, 0.9f, 1f, 1f, 2f);
        m.main.texture.texture = KilaTextures.RIPPLE;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.distortion.setEnable(true);
        m.distortion.texture.polar = true;
        m.distortion.texture.scroll(0f, 0.4f);
        m.distortion.strength.value = 0.04f;
        m.mask1.setEnable(true);
        m.mask1.power = 0.4f;
    });

    /** A 4-point twinkle: brightness from Particle Random, Camera Fade. */
    public static final Preset SPARKLE = new Preset("sparkle", Category.BASIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.cameraOffset = 0.2f;
        m.color = new HDRColor(1f, 0.95f, 0.8f, 1f, 2.8f);
        m.main.texture.texture = KilaTextures.SPARKLE;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.intensity.value = 0.5f;
        m.intensity.bind(KilaDriver.Source.RANDOM, 1.4f);
        m.cameraFade.setEnable(true);
        m.cameraFade.near = 0.3f;
        m.cameraFade.range = 1.5f;
    });

    /** A vertical column of light, Additive, Clip wrap. */
    public static final Preset SHAFT = new Preset("shaft", Category.BASIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.85f, 0.95f, 1f, 1f, 2.2f);
        m.main.texture.texture = KilaTextures.SHAFT;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.distortion.setEnable(true);
        m.distortion.texture.scroll(0.05f, 0.35f);
        m.distortion.strength.value = 0.03f;
    });

    /** A crescent slash spinning slowly, Color Ramp by alpha. */
    public static final Preset CRESCENT = new Preset("crescent", Category.MAGIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(1f, 1f, 1f, 1f, 2.4f);
        m.main.texture.texture = KilaTextures.CRESCENT;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.main.texture.rotationSpeed = 40;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.ALPHA;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF140028, 0xFF7A20E8, 0xFFD0A0FF, 0xFFFFFFFF).getRgbP());
        m.mask1.setEnable(true);
        m.mask1.power = 0.45f;
    });

    /** A rising smoke tendril, a crawling Second Layer and a little UV Distortion. */
    public static final Preset WISP = new Preset("wisp", Category.FIRE_SMOKE, m -> {
        m.color = new HDRColor(0.82f, 0.84f, 0.88f, 0.9f, 1f);
        m.main.texture.texture = KilaTextures.WISP;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.layer.setEnable(true);
        m.layer.texture.texture = KilaTextures.NOISE_CLOUD;
        m.layer.texture.scroll(0.03f, 0.12f);
        m.layer.strength.value = 0.45f;
        m.distortion.setEnable(true);
        m.distortion.strength.value = 0.035f;
        m.distortion.texture.scroll(0.02f, 0.2f);
        m.depth.setEnable(true);
    });

    /** A lightning bolt sprite, Additive, UV Distortion crawling along it. */
    public static final Preset ARC = new Preset("arc", Category.MAGIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.55f, 0.75f, 1f, 1f, 3.2f);
        m.main.texture.texture = KilaTextures.ARC;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.distortion.setEnable(true);
        m.distortion.strength.value = 0.05f;
        m.distortion.texture.scroll(1.4f, -0.6f);
        m.intensity.value = 0.7f;
        m.intensity.bind(KilaDriver.Source.RANDOM, 0.9f);
    });

    /** A smoke puff lit by the six lightmaps it was baked with: the key light and the dynamic ones fall on its sides. */
    public static final Preset LIT_SMOKE = new Preset("lit_smoke", Category.FIRE_SMOKE, m -> {
        m.color = new HDRColor(0.85f, 0.85f, 0.9f, 1f, 1f);
        m.main.texture.texture = KilaTextures.SMOKE6_POS;
        m.shading.setEnable(true);
        m.shading.model = KilaShading.Model.SIX_WAY;
        m.shading.ambient = 0.7f;
        m.shading.lightColor = new HDRColor(1f, 0.95f, 0.85f, 1f, 1.6f);
        m.depth.setEnable(true);
    });

    /** For a sphere or other mesh: glass that bends what is behind it, a painted reflection, a bright rim. */
    public static final Preset CRYSTAL = new Preset("crystal", Category.SURFACE, Target.MESH, m -> {
        m.worldLight = false;
        m.doubleSided = true;
        m.backFacesFirst = true;
        m.color = new HDRColor(0.55f, 0.8f, 1f, 0.35f, 1f);
        m.main.texture.texture = KilaTextures.WHITE;
        m.shading.setEnable(true);
        m.shading.normal = KilaShading.Normal.MESH;
        m.shading.bump = KilaShading.Bump.NORMAL_MAP;
        m.shading.bumpStrength = 0.4f;
        m.matcap.setEnable(true);
        m.fresnel.setEnable(true);
        m.fresnel.color = new HDRColor(0.5f, 0.85f, 1f, 1f, 2f);
        m.screenDistortion.setEnable(true);
        m.screenDistortion.source = KilaScreenDistortion.Source.NORMAL;
        m.screenDistortion.strength.value = 0.03f;
        m.screenDistortion.chromatic = 0.3f;
    });

    /** Frosted translucent mesh: subtle painted reflection, cool rim and gentle soft intersections. */
    public static final Preset FROSTED_GLASS = new Preset("frosted_glass", Category.SURFACE, Target.MESH, m -> {
        m.blend = BlendMode.Preset.PREMULTIPLIED;
        m.worldLight = false;
        m.doubleSided = true;
        m.backFacesFirst = true;
        m.color = new HDRColor(0.58f, 0.82f, 1f, 0.32f, 1f);
        m.main.texture.texture = KilaTextures.WHITE;
        m.matcap.setEnable(true);
        m.matcap.blend = KilaMatcap.Blend.ADD;
        m.matcap.strength = 0.55f;
        m.fresnel.setEnable(true);
        m.fresnel.power = 2.4f;
        m.fresnel.color = new HDRColor(0.45f, 0.82f, 1f, 1f, 2.6f);
        m.depth.setEnable(true);
        m.depth.distance = 0.45f;
    });

    /** A ball of fog that thins towards its edge from any side, and where the ground cuts it. */
    public static final Preset FOG_BALL = new Preset("fog_ball", Category.FIRE_SMOKE, m -> {
        m.color = new HDRColor(0.8f, 0.85f, 0.8f, 0.8f, 1f);
        m.main.texture.texture = KilaTextures.NOISE_CLOUD;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.scroll(0.03f, 0.05f);
        m.volume.setEnable(true);
        m.volume.density = 1.5f;
        m.shading.setEnable(true);
        m.shading.wrap = 0.8f;
    });

    /** Hit sparks: each its own brightness, pulled towards the camera so the wall they come off doesn't cut them. */
    public static final Preset SPARKS = new Preset("sparks", Category.BASIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.cameraOffset = 0.3f;
        m.color = new HDRColor(1f, 0.75f, 0.35f, 1f, 3f);
        m.main.texture.texture = KilaTextures.SPARK;
        m.intensity.value = 0.4f;
        m.intensity.bind(KilaDriver.Source.RANDOM, 1.2f);
        m.cameraFade.setEnable(true);
        m.cameraFade.near = 0.3f;
        m.cameraFade.range = 1.5f;
    });

    /** A billowing puff played once over the lifetime: a random puff per particle, its frames blended along their motion. */
    public static final Preset EXPLOSION_SMOKE = new Preset("explosion_smoke", Category.FIRE_SMOKE, m -> {
        m.blend = BlendMode.Preset.PREMULTIPLIED;
        m.color = new HDRColor(0.55f, 0.52f, 0.5f, 1f, 1f);
        m.main.texture.texture = KilaTextures.SMOKE_BURST;
        m.main.flipbook = true;
        m.main.columns = 8;
        m.main.rows = 4;
        m.main.randomRow = true;
        m.main.frameSource = KilaMainTexture.FrameSource.DRIVER;
        m.main.frame.bind(KilaDriver.Source.LIFE, 1);
        m.main.frameBlend = true;
        m.main.motionVectors = true;
        m.main.motionTexture.texture = KilaTextures.SMOKE_BURST_MV;
        m.depth.setEnable(true);
        m.lit.setEnable(true);
    });

    /** A hot animated blast: the smoke flipbook is colour-ramped from cinder red through orange to a white-hot core. */
    public static final Preset LAVA_BURST = new Preset("lava_burst", Category.FIRE_SMOKE, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.color = new HDRColor(1f, 0.72f, 0.42f, 1f, 1.5f);
        m.emission = new HDRColor(1f, 0.12f, 0.015f, 1f, 1.5f);
        m.main.texture.texture = KilaTextures.SMOKE_BURST;
        m.main.flipbook = true;
        m.main.columns = 8;
        m.main.rows = 4;
        m.main.randomRow = true;
        m.main.frameSource = KilaMainTexture.FrameSource.DRIVER;
        m.main.frame.bind(KilaDriver.Source.LIFE, 1);
        m.main.frameBlend = true;
        m.main.motionVectors = true;
        m.main.motionTexture.texture = KilaTextures.SMOKE_BURST_MV;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.LUMINANCE;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF39120C, 0xFFE03812, 0xFFFF9A24, 0xFFFFF2B0).getRgbP());
        m.dissolve.setEnable(true);
        m.dissolve.texture.texture = KilaTextures.NOISE_CLOUD;
        m.dissolve.progress.value = 0.35f;
        m.dissolve.progress.bind(KilaDriver.Source.LIFE, 0.65f);
        m.dissolve.direction = KilaDissolve.Direction.RADIAL;
        m.dissolve.softness = 0.12f;
        m.depth.setEnable(true);
        m.lit.setEnable(true);
    });

    /** Fades to the tail and as points age, soft along its edges, waving, two noises crawling through it, colour fringes. */
    public static final Preset ENERGY_TRAIL = new Preset("energy_trail", Category.MAGIC, Target.TRAIL, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.4f, 0.85f, 1f, 1f, 2.5f);
        m.backColor = new HDRColor(1f, 0.45f, 0.9f, 1f, 1f);
        m.alpha.value = 0;
        m.alpha.bind(KilaDriver.Source.POINT_T, 1);
        m.intensity.value = 1.5f;
        m.intensity.bind(KilaDriver.Source.POINT_LIFE, -1);
        m.main.texture.texture = KilaTextures.TRAIL;
        m.main.texture.wrap(KilaTexture.Wrap.CLAMP, KilaTexture.Wrap.CLAMP);
        m.main.dispersion = true;
        m.main.dispersionDirection.set(0, 1);
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.ADD;
        m.layer.texture.texture = KilaTextures.NOISE_FLOW;
        m.layer.texture.tiling(2, 1).scroll(-1.5f, 0);
        m.layer.strength.value = 0.6f;
        m.layer2.setEnable(true);
        m.layer2.blend = KilaLayer.Blend.SCREEN;
        m.layer2.texture.texture = KilaTextures.NOISE_CELLS;
        m.layer2.texture.tiling(3, 1).scroll(-0.6f, 0.2f);
        m.layer2.strength.value = 0.5f;
        // the ramp turned across the ribbon and mirrored: 0 at both edges, 1 down the middle
        m.mask2.setEnable(true);
        m.mask2.texture.texture = KilaTextures.GRADIENT_LINEAR;
        m.mask2.texture.rotation = 90;
        m.mask2.texture.tiling(2, 1);
        m.mask2.texture.wrapU = KilaTexture.Wrap.MIRROR;
        m.uvEffects.setEnable(true);
        m.uvEffects.twirl.value = 0;
        m.uvEffects.waveAmplitude.value = 0.06f;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.U;
        m.ramp.mode = KilaRamp.Mode.MULTIPLY;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFFFFFFFF, 0xFF7FE6FF, 0xFF6A4BFF).getRgbP());
    });

    /** A cold animated burst: the smoke flipbook runs from deep blue through cyan to a frosty white core. */
    public static final Preset FROST_BURST = new Preset("frost_burst", Category.STYLIZED, m -> {
        m.blend = BlendMode.Preset.PREMULTIPLIED;
        m.color = new HDRColor(0.62f, 0.84f, 1f, 0.92f, 1.1f);
        m.main.texture.texture = KilaTextures.SMOKE_FLIP;
        m.main.flipbook = true;
        m.main.columns = 4;
        m.main.rows = 4;
        m.main.randomRow = true;
        m.main.frameSource = KilaMainTexture.FrameSource.DRIVER;
        m.main.frame.bind(KilaDriver.Source.LIFE, 1);
        m.main.frameBlend = true;
        m.main.motionVectors = true;
        m.main.motionTexture.texture = KilaTextures.SMOKE_FLIP_MV;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.LUMINANCE;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF101B48, 0xFF397FC7, 0xFF9CEBFF, 0xFFFFFFFF).getRgbP());
        m.distortion.setEnable(true);
        m.distortion.strength.value = 0.018f;
        m.distortion.texture.texture = KilaTextures.NOISE_FLOW;
        m.distortion.texture.scroll(0.08f, -0.12f);
        m.depth.setEnable(true);
        m.lit.setEnable(true);
    });

    /** A calm aurora ribbon: soft premultiplied colour, two slow flow layers and a feathered trail edge. */
    public static final Preset AURORA_TRAIL = new Preset("aurora_trail", Category.MAGIC, Target.TRAIL, m -> {
        m.blend = BlendMode.Preset.PREMULTIPLIED;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.55f, 0.92f, 0.82f, 0.82f, 1.4f);
        m.alpha.value = 0;
        m.alpha.bind(KilaDriver.Source.POINT_T, 1);
        m.intensity.value = 1.1f;
        m.intensity.bind(KilaDriver.Source.POINT_LIFE, -0.45f);
        m.main.texture.texture = KilaTextures.TRAIL;
        m.main.texture.wrap(KilaTexture.Wrap.CLAMP, KilaTexture.Wrap.CLAMP);
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.ADD;
        m.layer.texture.texture = KilaTextures.NOISE_FLOW;
        m.layer.texture.tiling(1.5f, 1).scroll(-0.32f, 0.04f);
        m.layer.strength.value = 0.35f;
        m.layer2.setEnable(true);
        m.layer2.blend = KilaLayer.Blend.SCREEN;
        m.layer2.texture.texture = KilaTextures.NOISE_CLOUD;
        m.layer2.texture.tiling(2, 1).scroll(-0.12f, 0.08f);
        m.layer2.strength.value = 0.22f;
        m.mask2.setEnable(true);
        m.mask2.texture.texture = KilaTextures.GRADIENT_LINEAR;
        m.mask2.texture.rotation = 90;
        m.mask2.texture.tiling(2, 1);
        m.mask2.texture.wrapU = KilaTexture.Wrap.MIRROR;
        m.uvEffects.setEnable(true);
        m.uvEffects.waveAmplitude.value = 0.035f;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.U;
        m.ramp.mode = KilaRamp.Mode.MULTIPLY;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF7CFFD3, 0xFF52D7FF, 0xFF927BFF, 0xFF7CFFD3).getRgbP());
    });

    /** Forms from its core outwards as the emitter's custom data (stream 0, x) rises from 0 to 1; invisible at 0. */
    public static final Preset CHARGE_ORB = new Preset("charge_orb", Category.MAGIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.color = new HDRColor(0.7f, 0.35f, 1f, 1f, 2f);
        m.emission = new HDRColor(0.25f, 0.1f, 0.4f, 1f, 1f);
        m.main.texture.texture = KilaTextures.GLOW;
        m.dissolve.setEnable(true);
        m.dissolve.texture.texture = KilaTextures.NOISE_CELLS;
        m.dissolve.texture.scroll(0.15f, 0.1f);
        m.dissolve.progress.value = 1;
        m.dissolve.progress.bindCustom(0, 0, -1);
        m.dissolve.direction = KilaDissolve.Direction.RADIAL;
        m.dissolve.directionInvert = true;
        m.dissolve.edgeWidth.value = 0.12f;
        m.dissolve.edgeGradient = true;
        m.dissolve.edgeGradientColors.getRgbP().clear();
        m.dissolve.edgeGradientColors.getRgbP().addAll(new GradientColor(0xFFFFFFFF, 0xFFE0A0FF, 0xFF8030FF).getRgbP());
        m.uvEffects.setEnable(true);
        m.uvEffects.twirl.value = 0;
        m.uvEffects.shear.set(4, 4);
        m.uvEffects.rippleAmplitude = 0.02f;
    });

    /** A burn mark multiplied onto the ground, eaten away from its ragged edge over the lifetime. */
    public static final Preset SCORCH_MARK = new Preset("scorch_mark", Category.GROUND, m -> {
        m.blend = BlendMode.Preset.MULTIPLY;
        m.worldLight = false;
        m.fog = false;
        m.color = new HDRColor(0.2f, 0.16f, 0.13f, 1f, 1f);
        m.main.texture.texture = KilaTextures.SOFT_CIRCLE;
        m.layer.setEnable(true);
        m.layer.texture.texture = KilaTextures.NOISE_CLOUD;
        m.layer.texture.scroll(0, 0);
        m.layer.strength.value = 0.5f;
        m.projection.setEnable(true);
        m.projection.depth = 2;
        m.projection.size = 0.8f;
        m.dissolve.setEnable(true);
        m.dissolve.progress.value = 0;
        m.dissolve.progress.bind(KilaDriver.Source.LIFE, 1);
        m.dissolve.direction = KilaDissolve.Direction.TEXTURE;
        m.dissolve.directionTexture.texture = KilaTextures.GRADIENT_RADIAL;
        m.dissolve.directionWeight = 0.6f;
        m.dissolve.edgeWidth.value = 0;
    });

    /** Light through water, projected onto the ground in world space so it stays put, its cells bent by a flow map. */
    public static final Preset CAUSTICS = new Preset("caustics", Category.GROUND, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.color = new HDRColor(0.55f, 0.85f, 1f, 1f, 3f);
        m.alphaSharpen = 2;
        m.main.texture.texture = KilaTextures.NOISE_CELLS;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.uvSource = KilaTexture.UvSource.WORLD_XZ;
        // eight cells per repeat: one about every block
        m.main.texture.tiling(0.125f, 0.125f);
        m.distortion.setEnable(true);
        m.distortion.mode = KilaDistortion.Mode.FLOW_MAP;
        m.distortion.strength.value = 0.15f;
        m.mask1.setEnable(true);
        m.mask1.power = 0.5f;
        m.projection.setEnable(true);
        m.projection.depth = 2;
        m.projection.size = 1;
    });

    /** For a big sphere through the terrain: a faint shell, bright bands pulsing out along where it cuts the ground. */
    public static final Preset SCAN_PULSE = new Preset("scan_pulse", Category.GROUND, Target.MESH, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.3f, 0.8f, 1f, 0.15f, 1f);
        m.main.texture.texture = KilaTextures.WHITE;
        m.fresnel.setEnable(true);
        m.fresnel.mode = KilaFresnel.Mode.FADE_CENTER;
        m.depth.setEnable(true);
        m.depth.contactWidth.value = 1.2f;
        m.depth.contactStrength.value = 1;
        m.depth.contactFollowsShape = false;
        m.depth.measure = KilaDepth.Measure.SURFACE;
        m.depth.contactPulses = 3;
        m.depth.contactPulseSpeed = 1.5f;
        m.depth.contactGradient = true;
    });

    /** Scanlines in screen space, a bright rim, slices jittering sideways. */
    public static final Preset HOLOGRAM = new Preset("hologram", Category.SURFACE, Target.MESH, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.color = new HDRColor(0.3f, 0.9f, 1f, 0.5f, 1.2f);
        m.main.texture.texture = KilaTextures.WHITE;
        m.layer.setEnable(true);
        m.layer.texture.texture = KilaTextures.GRADIENT_LINEAR;
        m.layer.texture.uvSource = KilaTexture.UvSource.SCREEN;
        m.layer.texture.rotation = 90;
        m.layer.texture.tiling(120, 1).scroll(0.4f, 0);
        m.layer.strength.value = 0.8f;
        m.fresnel.setEnable(true);
        m.fresnel.color = new HDRColor(0.4f, 0.95f, 1f, 1f, 3f);
        m.vertexOffset.setEnable(true);
        m.vertexOffset.direction = KilaVertexOffset.Direction.AXIS;
        m.vertexOffset.axis.set(1, 0, 0);
        m.vertexOffset.bidirectional = true;
        m.vertexOffset.strength.value = 0.04f;
        m.vertexOffset.texture.tiling(1, 6).scroll(0, 2.5f);
    });

    /** A projected toxic puddle: slow cell flow, a soft contact edge and a lifetime-driven dissolve. */
    public static final Preset SLIME_PUDDLE = new Preset("slime_puddle", Category.GROUND, m -> {
        m.blend = BlendMode.Preset.PREMULTIPLIED;
        m.worldLight = false;
        m.fog = false;
        m.color = new HDRColor(0.42f, 0.9f, 0.16f, 0.78f, 1.15f);
        m.main.texture.texture = KilaTextures.NOISE_CELLS;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.uvSource = KilaTexture.UvSource.WORLD_XZ;
        m.main.texture.tiling(0.7f, 0.7f);
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.ADD;
        m.layer.texture.texture = KilaTextures.NOISE_FLOW;
        m.layer.texture.scroll(0.03f, 0.02f);
        m.layer.strength.value = 0.35f;
        m.distortion.setEnable(true);
        m.distortion.mode = KilaDistortion.Mode.FLOW_MAP;
        m.distortion.texture.texture = KilaTextures.FLOW_SWIRL;
        m.distortion.strength.value = 0.025f;
        m.distortion.flowSpeed = 0.18f;
        m.dissolve.setEnable(true);
        m.dissolve.texture.texture = KilaTextures.NOISE_CLOUD;
        m.dissolve.progress.value = 1;
        m.dissolve.progress.bind(KilaDriver.Source.LIFE, 1);
        m.dissolve.direction = KilaDissolve.Direction.RADIAL;
        m.dissolve.softness = 0.12f;
        m.projection.setEnable(true);
        m.projection.depth = 1.5f;
        m.projection.size = 0.85f;
        m.depth.setEnable(true);
        m.depth.distance = 0.55f;
    });

    /** Dark, height-bumped rock with hot cracks; an opaque, lit alternative to the clean Debris material. */
    public static final Preset EMBER_ROCK = new Preset("ember_rock", Category.SURFACE, Target.MESH, m -> {
        m.blend = BlendMode.Preset.OPAQUE;
        m.depthWrite = true;
        m.color = new HDRColor(0.75f, 0.68f, 0.6f, 1f, 1f);
        m.main.texture.texture = KilaTextures.NOISE_PERLIN;
        m.shading.setEnable(true);
        m.shading.normal = KilaShading.Normal.MESH;
        m.shading.bump = KilaShading.Bump.HEIGHT;
        m.shading.bumpStrength = 1.8f;
        m.shading.ambient = 0.38f;
        m.lit.setEnable(true);
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.LUMINANCE;
        m.ramp.mode = KilaRamp.Mode.MULTIPLY;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF24110D, 0xFF5C2112, 0xFFD24A12, 0xFFFFB02E).getRgbP());
        m.emission = new HDRColor(0.8f, 0.18f, 0.035f, 1f, 0.8f);
    });

    /** A rising violet soul flame, its edge dissolving into a cyan-magenta burn. */
    public static final Preset SOUL_FLAME = new Preset("soul_flame", Category.FIRE_SMOKE, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.vertexColor = KilaMaterial.VertexColor.RGB_ONLY;
        m.color = new HDRColor(0.62f, 0.34f, 1f, 1f, 2.1f);
        m.main.texture.texture = KilaTextures.WISP;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.ADD;
        m.layer.texture.texture = KilaTextures.NOISE_FLOW;
        m.layer.texture.scroll(0.04f, 0.2f);
        m.layer.strength.value = 0.45f;
        m.distortion.setEnable(true);
        m.distortion.texture.noise(KilaTexture.Noise.SIMPLE, 5).scroll(0.08f, 0.24f);
        m.distortion.strength.value = 0.035f;
        m.dissolve.setEnable(true);
        m.dissolve.texture.texture = KilaTextures.NOISE_CELLS;
        m.dissolve.progress.value = 1;
        m.dissolve.progress.bind(KilaDriver.Source.LIFE, 1);
        m.dissolve.direction = KilaDissolve.Direction.V;
        m.dissolve.softness = 0.16f;
        m.dissolve.edgeWidth.value = 0.08f;
        m.dissolve.edgeGradient = true;
        m.dissolve.edgeGradientColors.getRgbP().clear();
        m.dissolve.edgeGradientColors.getRgbP().addAll(new GradientColor(0xFFFFFFFF, 0xFF63E9FF, 0xFFB951FF, 0x00B951FF).getRgbP());
        m.depth.setEnable(true);
    });

    /** Opaque rock, bumped by a procedural height and lit by the world and the dynamic lights. */
    public static final Preset DEBRIS = new Preset("debris", Category.SURFACE, Target.MESH, m -> {
        m.blend = BlendMode.Preset.OPAQUE;
        m.depthWrite = true;
        m.color = new HDRColor(0.8f, 0.72f, 0.64f, 1f, 1f);
        m.main.texture.texture = KilaTextures.NOISE_PERLIN;
        m.shading.setEnable(true);
        m.shading.normal = KilaShading.Normal.MESH;
        m.shading.bump = KilaShading.Bump.HEIGHT;
        m.shading.bumpStrength = 1.5f;
        m.shading.ambient = 0.45f;
        m.lit.setEnable(true);
    });

    /** Heat behind an engine: the hazes of many particles add up instead of each bending the last. */
    public static final Preset JET_EXHAUST = new Preset("jet_exhaust", Category.SCREEN, m -> {
        m.worldLight = false;
        m.fog = false;
        m.main.texture.texture = KilaTextures.SOFT_CIRCLE;
        m.screenDistortion.setEnable(true);
        m.screenDistortion.stack = true;
        m.screenDistortion.texture.scroll(0, 1.2f);
        m.screenDistortion.strength.value = 0.004f;
        m.depth.setEnable(true);
    });

    /** Hand-drawn fire: a few colour bands, a hard cut-out edge with an outline, drawn on twelves with a boil. */
    public static final Preset TOON_FIRE = new Preset("toon_fire", Category.STYLIZED, m -> {
        m.worldLight = false;
        m.alphaClip = 0.35f;
        m.frameRate = 12;
        m.boil = 0.02f;
        m.main.texture.texture = KilaTextures.GLOW;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.ALPHA;
        m.ramp.offset.bind(KilaDriver.Source.LIFE, -0.4f);
        m.dissolve.setEnable(true);
        m.dissolve.texture.noise(KilaTexture.Noise.VORONOI, 5).scroll(0, -0.8f);
        m.dissolve.progress.value = 0.1f;
        m.dissolve.progress.bind(KilaDriver.Source.LIFE, 0.8f);
        m.dissolve.softness = 0.01f;
        m.dissolve.edgeWidth.value = 0.06f;
        m.dissolve.edge = KilaDissolve.Edge.REPLACE;
        m.dissolve.edgeColor = new HDRColor(0.35f, 0.04f, 0.02f, 1f, 1f);
        m.colorAdjust.setEnable(true);
        m.colorAdjust.posterize = 4;
    });

    /** Pixel art: a looping puff from a random frame, snapped to a coarse grid and sampled without filtering, twinkling. */
    public static final Preset PIXEL_SPELL = new Preset("pixel_spell", Category.STYLIZED, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.color = new HDRColor(0.75f, 0.4f, 1f, 1f, 2f);
        m.main.texture.texture = KilaTextures.SMOKE_FLIP;
        m.main.texture.nearest = true;
        m.main.flipbook = true;
        m.main.randomStart = true;
        m.mask1.setEnable(true);
        m.mask1.texture.noise(KilaTexture.Noise.SIMPLE, 24);
        m.mask1.power = 0.6f;
        m.pixelate.setEnable(true);
        m.pixelate.pixels = 24;
    });

    /** A gentle healing field: a soft pulse, crawling cloud detail and a mint-to-gold luminance ramp. */
    public static final Preset HEALING_AURA = new Preset("healing_aura", Category.BASIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.color = new HDRColor(0.45f, 1f, 0.7f, 0.8f, 1.4f);
        m.alpha.value = 1;
        m.alpha.bind(KilaDriver.Source.LIFE, -1);
        m.main.texture.texture = KilaTextures.GLOW;
        m.main.texture.rotationSpeed = 18;
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.SCREEN;
        m.layer.texture.texture = KilaTextures.NOISE_CLOUD;
        m.layer.texture.scroll(0.04f, -0.06f);
        m.layer.strength.value = 0.28f;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.LUMINANCE;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF75DCA0, 0xFF9DFFD0, 0xFFFFE8A0).getRgbP());
    });

    /** Slow toxic vapor with a lifetime flipbook, cell detail and gentle flow-map distortion. */
    public static final Preset POISON_MIST = new Preset("poison_mist", Category.FIRE_SMOKE, m -> {
        m.blend = BlendMode.Preset.PREMULTIPLIED;
        m.color = new HDRColor(0.38f, 0.78f, 0.16f, 0.7f, 1f);
        m.main.texture.texture = KilaTextures.SMOKE_FLIP;
        m.main.flipbook = true;
        m.main.columns = 4;
        m.main.rows = 4;
        m.main.randomRow = true;
        m.main.frameSource = KilaMainTexture.FrameSource.DRIVER;
        m.main.frame.bind(KilaDriver.Source.LIFE, 1);
        m.main.frameBlend = true;
        m.main.motionVectors = true;
        m.main.motionTexture.texture = KilaTextures.SMOKE_FLIP_MV;
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.MULTIPLY;
        m.layer.texture.texture = KilaTextures.NOISE_CELLS;
        m.layer.texture.scroll(0.03f, 0.02f);
        m.layer.strength.value = 0.3f;
        m.distortion.setEnable(true);
        m.distortion.mode = KilaDistortion.Mode.FLOW_MAP;
        m.distortion.texture.texture = KilaTextures.FLOW_SWIRL;
        m.distortion.strength.value = 0.018f;
        m.depth.setEnable(true);
        m.lit.setEnable(true);
    });

    /** A stylized water ring: ripples turn while a slow flow map bends its translucent edge. */
    public static final Preset WATER_BURST = new Preset("water_burst", Category.STYLIZED, m -> {
        m.blend = BlendMode.Preset.PREMULTIPLIED;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.32f, 0.78f, 1f, 0.8f, 1.25f);
        m.alpha.value = 1;
        m.alpha.bind(KilaDriver.Source.LIFE, -1);
        m.main.texture.texture = KilaTextures.RIPPLE;
        m.main.texture.rotationSpeed = 24;
        m.main.texture.wrap(KilaTexture.Wrap.CLAMP, KilaTexture.Wrap.CLAMP);
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.ADD;
        m.layer.texture.texture = KilaTextures.NOISE_FLOW;
        m.layer.texture.scroll(0.08f, -0.06f);
        m.layer.strength.value = 0.22f;
        m.distortion.setEnable(true);
        m.distortion.mode = KilaDistortion.Mode.FLOW_MAP;
        m.distortion.texture.texture = KilaTextures.FLOW_SWIRL;
        m.distortion.strength.value = 0.012f;
        m.depth.setEnable(true);
    });

    /** A comet ribbon, hot at its head and fading toward the tail; intended for the Trails renderer. */
    public static final Preset COMET_TRAIL = new Preset("comet_trail", Category.MAGIC, Target.TRAIL, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(1f, 0.68f, 0.28f, 1f, 2f);
        m.alpha.value = 0;
        m.alpha.bind(KilaDriver.Source.POINT_T, 1);
        m.intensity.value = 1.8f;
        m.intensity.bind(KilaDriver.Source.POINT_LIFE, -0.6f);
        m.main.texture.texture = KilaTextures.TRAIL;
        m.main.texture.wrap(KilaTexture.Wrap.CLAMP, KilaTexture.Wrap.CLAMP);
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.ADD;
        m.layer.texture.texture = KilaTextures.STAR_RAYS;
        m.layer.texture.tiling(1.5f, 1).scroll(-0.25f, 0);
        m.layer.strength.value = 0.28f;
        m.mask2.setEnable(true);
        m.mask2.texture.texture = KilaTextures.GRADIENT_LINEAR;
        m.mask2.texture.rotation = 90;
        m.mask2.texture.tiling(2, 1);
        m.mask2.texture.wrapU = KilaTexture.Wrap.MIRROR;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.U;
        m.ramp.mode = KilaRamp.Mode.MULTIPLY;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFFFF5520, 0xFFFFC13D, 0xFFFFFFFF).getRgbP());
    });

    /** A rotating void aperture for a sphere mesh, with a crawling edge and a bright view-angle rim. */
    public static final Preset VOID_GATE = new Preset("void_gate", Category.MAGIC, Target.MESH, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.34f, 0.08f, 0.7f, 0.72f, 1.6f);
        m.main.texture.texture = KilaTextures.VORTEX;
        m.main.texture.rotationSpeed = 12;
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.SCREEN;
        m.layer.texture.texture = KilaTextures.NOISE_CELLS;
        m.layer.texture.polar = true;
        m.layer.texture.scroll(0, 0.12f);
        m.layer.strength.value = 0.3f;
        m.fresnel.setEnable(true);
        m.fresnel.power = 2.5f;
        m.fresnel.color = new HDRColor(0.55f, 0.22f, 1f, 1f, 3f);
        m.uvEffects.setEnable(true);
        m.uvEffects.twirl.value = 0.12f;
    });

    /** A narrow golden shaft with a lifetime fade, depth softening and a sunlit colour ramp. */
    public static final Preset SOLAR_BEAM = new Preset("solar_beam", Category.BASIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.color = new HDRColor(1f, 0.72f, 0.32f, 0.9f, 1.5f);
        m.alpha.value = 1;
        m.alpha.bind(KilaDriver.Source.LIFE, -1);
        m.main.texture.texture = KilaTextures.SHAFT;
        m.main.texture.wrap(KilaTexture.Wrap.CLAMP, KilaTexture.Wrap.CLIP);
        m.main.texture.scroll(0, 0.08f);
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.V;
        m.ramp.mode = KilaRamp.Mode.MULTIPLY;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFFFF8B28, 0xFFFFE7A0, 0xFFFFFFFF).getRgbP());
        m.depth.setEnable(true);
    });

    /** A projected rune sigil that stays on floors and fades out through its lifetime. */
    public static final Preset RUNE_FLOOR = new Preset("rune_floor", Category.GROUND, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.34f, 0.78f, 1f, 0.85f, 1.8f);
        m.alpha.value = 1;
        m.alpha.bind(KilaDriver.Source.LIFE, -1);
        m.main.texture.texture = KilaTextures.RUNE_CIRCLE;
        m.main.texture.rotationSpeed = 9;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.ADD;
        m.layer.texture.texture = KilaTextures.NOISE_CELLS;
        m.layer.texture.polar = true;
        m.layer.texture.scroll(0, 0.1f);
        m.layer.strength.value = 0.2f;
        m.projection.setEnable(true);
        m.projection.depth = 1.5f;
        m.projection.size = 1.1f;
        m.depth.setEnable(true);
    });

    /** A refracting prism shield: translucent body, noise cells, a cyan rim and a spectral gradient. */
    public static final Preset PRISMATIC_SHIELD = new Preset("prismatic_shield", Category.SURFACE, Target.MESH, m -> {
        m.blend = BlendMode.Preset.PREMULTIPLIED;
        m.worldLight = false;
        m.color = new HDRColor(0.42f, 0.68f, 1f, 0.28f, 1f);
        m.main.texture.texture = KilaTextures.WHITE;
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.SCREEN;
        m.layer.texture.texture = KilaTextures.NOISE_CELLS;
        m.layer.texture.scroll(0.04f, 0.08f);
        m.layer.strength.value = 0.24f;
        m.fresnel.setEnable(true);
        m.fresnel.power = 3;
        m.fresnel.color = new HDRColor(0.3f, 0.85f, 1f, 1f, 2.5f);
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.U;
        m.ramp.mode = KilaRamp.Mode.MULTIPLY;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF65E8FF, 0xFF8D8BFF, 0xFFFF75CB).getRgbP());
        m.depth.setEnable(true);
    });

    /** Faceted crystal on a mesh, using the bundled matcap instead of loading any extra texture. */
    public static final Preset CRYSTAL_SHARD = new Preset("crystal_shard", Category.SURFACE, Target.MESH, m -> {
        m.blend = BlendMode.Preset.PREMULTIPLIED;
        m.color = new HDRColor(0.64f, 0.86f, 1f, 0.55f, 1.1f);
        m.main.texture.texture = KilaTextures.MATCAP_GLASS;
        m.shading.setEnable(true);
        m.shading.normal = KilaShading.Normal.MESH;
        m.shading.bump = KilaShading.Bump.HEIGHT;
        m.shading.bumpStrength = 0.35f;
        m.shading.ambient = 0.25f;
        m.lit.setEnable(true);
        m.fresnel.setEnable(true);
        m.fresnel.power = 2;
        m.fresnel.color = new HDRColor(0.5f, 0.82f, 1f, 1f, 1.5f);
        m.depth.setEnable(true);
    });

    /** A small rotating dust column with a noisy silhouette, lifetime fade and gently waving UVs. */
    public static final Preset DUST_DEVIL = new Preset("dust_devil", Category.FIRE_SMOKE, m -> {
        m.blend = BlendMode.Preset.ALPHA;
        m.color = new HDRColor(0.72f, 0.54f, 0.32f, 0.78f, 1f);
        m.alpha.value = 1;
        m.alpha.bind(KilaDriver.Source.LIFE, -1);
        m.main.texture.texture = KilaTextures.VORTEX;
        m.main.texture.rotationSpeed = -16;
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.MULTIPLY;
        m.layer.texture.texture = KilaTextures.NOISE_CLOUD;
        m.layer.texture.tiling(1, 2).scroll(0.05f, -0.12f);
        m.layer.strength.value = 0.5f;
        m.mask1.setEnable(true);
        m.mask1.texture.texture = KilaTextures.GRADIENT_LINEAR;
        m.mask1.texture.rotation = 90;
        m.uvEffects.setEnable(true);
        m.uvEffects.waveAmplitude.value = 0.025f;
        m.depth.setEnable(true);
    });

    /** A compact cinder burst: fast smoke flipbook with a bright ember ramp and soft depth intersection. */
    public static final Preset EMBER_BURST = new Preset("ember_burst", Category.FIRE_SMOKE, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.color = new HDRColor(1f, 0.48f, 0.12f, 1f, 1.45f);
        m.main.texture.texture = KilaTextures.SMOKE_FLIP;
        m.main.flipbook = true;
        m.main.columns = 4;
        m.main.rows = 4;
        m.main.randomRow = true;
        m.main.frameSource = KilaMainTexture.FrameSource.DRIVER;
        m.main.frame.bind(KilaDriver.Source.LIFE, 1);
        m.main.frameBlend = true;
        m.main.motionVectors = true;
        m.main.motionTexture.texture = KilaTextures.SMOKE_FLIP_MV;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.LUMINANCE;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF35100A, 0xFFE34210, 0xFFFFA32A, 0xFFFFF1B5).getRgbP());
        m.dissolve.setEnable(true);
        m.dissolve.texture.texture = KilaTextures.NOISE_CLOUD;
        m.dissolve.progress.value = 0.2f;
        m.dissolve.progress.bind(KilaDriver.Source.LIFE, 0.75f);
        m.dissolve.softness = 0.1f;
        m.depth.setEnable(true);
    });

    /** A crisp blue ice ring that expands by animation and cools to white along its luminance. */
    public static final Preset FROST_RING = new Preset("frost_ring", Category.BASIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.5f, 0.8f, 1f, 1f, 1.7f);
        m.alpha.value = 1;
        m.alpha.bind(KilaDriver.Source.LIFE, -1);
        m.main.texture.texture = KilaTextures.RIPPLE;
        m.main.texture.rotationSpeed = 10;
        m.main.texture.wrap(KilaTexture.Wrap.CLAMP, KilaTexture.Wrap.CLAMP);
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.LUMINANCE;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF2366C5, 0xFF64D9FF, 0xFFE8FFFF).getRgbP());
        m.distortion.setEnable(true);
        m.distortion.strength.value = 0.008f;
        m.distortion.texture.texture = KilaTextures.NOISE_FLOW;
        m.depth.setEnable(true);
    });

    /** A sharp branching bolt with a violet-to-white colour ramp and a brief UV crackle. */
    public static final Preset CHAIN_LIGHTNING = new Preset("chain_lightning", Category.MAGIC, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.62f, 0.55f, 1f, 1f, 2.2f);
        m.main.texture.texture = KilaTextures.LIGHTNING_BOLT;
        m.main.texture.wrap(KilaTexture.Wrap.CLIP, KilaTexture.Wrap.CLIP);
        m.main.texture.rotationSpeed = 2;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.ALPHA;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF5020CC, 0xFF9B7AFF, 0xFFFFFFFF).getRgbP());
        m.uvEffects.setEnable(true);
        m.uvEffects.rippleAmplitude = 0.012f;
        m.depth.setEnable(true);
    });

    /** A blue-white plasma ribbon with flow detail and a soft feathered edge; use in the Trails renderer. */
    public static final Preset PLASMA_TRAIL = new Preset("plasma_trail", Category.MAGIC, Target.TRAIL, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.48f, 0.72f, 1f, 1f, 1.8f);
        m.alpha.value = 0;
        m.alpha.bind(KilaDriver.Source.POINT_T, 1);
        m.main.texture.texture = KilaTextures.TRAIL;
        m.main.texture.wrap(KilaTexture.Wrap.CLAMP, KilaTexture.Wrap.CLAMP);
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.ADD;
        m.layer.texture.texture = KilaTextures.NOISE_FLOW;
        m.layer.texture.tiling(2, 1).scroll(-0.42f, 0.03f);
        m.layer.strength.value = 0.32f;
        m.mask2.setEnable(true);
        m.mask2.texture.texture = KilaTextures.GRADIENT_LINEAR;
        m.mask2.texture.rotation = 90;
        m.mask2.texture.tiling(2, 1);
        m.mask2.texture.wrapU = KilaTexture.Wrap.MIRROR;
        m.distortion.setEnable(true);
        m.distortion.mode = KilaDistortion.Mode.FLOW_MAP;
        m.distortion.texture.texture = KilaTextures.FLOW_SWIRL;
        m.distortion.strength.value = 0.012f;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.U;
        m.ramp.mode = KilaRamp.Mode.MULTIPLY;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF49BFFF, 0xFFB8F5FF, 0xFFFFFFFF).getRgbP());
    });

    /** Impact mark from above: a burning radial decal projected onto the ground and eaten away by noise. */
    public static final Preset METEOR_STRIKE = new Preset("meteor_strike", Category.GROUND, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.fog = false;
        m.color = new HDRColor(1f, 0.32f, 0.06f, 0.85f, 1.4f);
        m.main.texture.texture = KilaTextures.GRADIENT_RADIAL;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.MULTIPLY;
        m.layer.texture.texture = KilaTextures.NOISE_PERLIN;
        m.layer.strength.value = 0.55f;
        m.projection.setEnable(true);
        m.projection.depth = 2.2f;
        m.projection.size = 1.3f;
        m.dissolve.setEnable(true);
        m.dissolve.texture.texture = KilaTextures.NOISE_CLOUD;
        m.dissolve.progress.value = 0;
        m.dissolve.progress.bind(KilaDriver.Source.LIFE, 1);
        m.dissolve.softness = 0.08f;
        m.depth.setEnable(true);
    });

    /** Dark volcanic glass on a mesh: opaque, world-lit and bumped from the bundled Perlin texture. */
    public static final Preset OBSIDIAN = new Preset("obsidian", Category.SURFACE, Target.MESH, m -> {
        m.blend = BlendMode.Preset.OPAQUE;
        m.depthWrite = true;
        m.color = new HDRColor(0.24f, 0.2f, 0.28f, 1f, 1f);
        m.main.texture.texture = KilaTextures.NOISE_PERLIN;
        m.shading.setEnable(true);
        m.shading.normal = KilaShading.Normal.MESH;
        m.shading.bump = KilaShading.Bump.HEIGHT;
        m.shading.bumpStrength = 0.8f;
        m.shading.ambient = 0.2f;
        m.lit.setEnable(true);
    });

    /** A drifting shadow veil for particles, with slow cloud breakup and a soft lifetime fade. */
    public static final Preset SHADOW_CLOAK = new Preset("shadow_cloak", Category.FIRE_SMOKE, m -> {
        m.blend = BlendMode.Preset.ALPHA;
        m.color = new HDRColor(0.16f, 0.13f, 0.24f, 0.66f, 1f);
        m.alpha.value = 1;
        m.alpha.bind(KilaDriver.Source.LIFE, -1);
        m.main.texture.texture = KilaTextures.SOFT_CIRCLE;
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.MULTIPLY;
        m.layer.texture.texture = KilaTextures.NOISE_CLOUD;
        m.layer.texture.tiling(1.5f, 1).scroll(0.018f, -0.024f);
        m.layer.strength.value = 0.72f;
        m.mask2.setEnable(true);
        m.mask2.texture.texture = KilaTextures.GRADIENT_RADIAL;
        m.mask2.power = 0.7f;
        m.depth.setEnable(true);
    });

    /** A mesh-bound star portal with a rotating rune plate and a radiant rim. */
    public static final Preset STAR_GATE = new Preset("star_gate", Category.MAGIC, Target.MESH, m -> {
        m.blend = BlendMode.Preset.ADDITIVE;
        m.worldLight = false;
        m.doubleSided = true;
        m.color = new HDRColor(0.7f, 0.36f, 1f, 0.55f, 1.8f);
        m.main.texture.texture = KilaTextures.STAR_RAYS;
        m.main.texture.rotationSpeed = -8;
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.SCREEN;
        m.layer.texture.texture = KilaTextures.RUNE_CIRCLE;
        m.layer.texture.rotationSpeed = 13;
        m.layer.strength.value = 0.5f;
        m.fresnel.setEnable(true);
        m.fresnel.power = 2.5f;
        m.fresnel.color = new HDRColor(0.62f, 0.38f, 1f, 1f, 3f);
        m.depth.setEnable(true);
    });

    /** A rising heat vent that bends the background in the screen-distortion pass. */
    public static final Preset STEAM_VENT = new Preset("steam_vent", Category.SCREEN, m -> {
        m.blend = BlendMode.Preset.ALPHA;
        m.worldLight = false;
        m.fog = false;
        m.color = new HDRColor(0.8f, 0.9f, 1f, 0.3f, 1f);
        m.main.texture.texture = KilaTextures.SOFT_CIRCLE;
        m.main.texture.colorMode = KilaTexture.ColorMode.LUMINANCE_AS_ALPHA;
        m.screenDistortion.setEnable(true);
        m.screenDistortion.strength.value = 0.003f;
        m.screenDistortion.texture.scroll(0, 0.8f);
        m.depth.setEnable(true);
    });

    /** A sandy blast, with a granular cloud layer, warm ramp and soft depth intersection. */
    public static final Preset SAND_BLAST = new Preset("sand_blast", Category.STYLIZED, m -> {
        m.blend = BlendMode.Preset.PREMULTIPLIED;
        m.color = new HDRColor(0.86f, 0.62f, 0.3f, 0.8f, 1f);
        m.main.texture.texture = KilaTextures.SOFT_CIRCLE;
        m.layer.setEnable(true);
        m.layer.blend = KilaLayer.Blend.MULTIPLY;
        m.layer.texture.texture = KilaTextures.NOISE_PERLIN;
        m.layer.texture.tiling(2, 2).scroll(0.12f, -0.08f);
        m.layer.strength.value = 0.4f;
        m.mask1.setEnable(true);
        m.mask1.texture.texture = KilaTextures.NOISE_CELLS;
        m.mask1.power = 0.72f;
        m.ramp.setEnable(true);
        m.ramp.input = KilaRamp.Input.LUMINANCE;
        m.ramp.gradient.getRgbP().clear();
        m.ramp.gradient.getRgbP().addAll(new GradientColor(0xFF70502D, 0xFFC8914A, 0xFFFFE0A0).getRgbP());
        m.depth.setEnable(true);
    });

    public static final List<Preset> ALL = List.of(GLOW, FLARE, SPARKLE, SPARKS, DISSOLVE, SLASH, SHOCKWAVE, RIPPLE, SHAFT,
            HEALING_AURA, SOLAR_BEAM,
            FIRE, SMOKE, SOFT_PUFF, WISP, SOUL_FLAME, EXPLOSION_SMOKE, LAVA_BURST, LIT_SMOKE, FOG_BALL, POISON_MIST, DUST_DEVIL,
            MAGIC_CIRCLE, HEXAGON, PORTAL, HUE_SWIRL, SPIRAL, BLACK_HOLE, STAR_BURST, CRESCENT, ARC, LIGHTNING, ARC_BOLT, CHARGE_ORB, ENERGY_TRAIL, AURORA_TRAIL, COMET_TRAIL, VOID_GATE,
            GROUND_CIRCLE, SCORCH_MARK, CAUSTICS, SLIME_PUDDLE, SCAN_PULSE, RUNE_FLOOR,
            ENERGY_SHIELD, FROSTED_GLASS, CRYSTAL, HOLOGRAM, EMBER_ROCK, DEBRIS, PRISMATIC_SHIELD, CRYSTAL_SHARD,
            HEAT_HAZE, JET_EXHAUST,
            FROST_BURST, WATER_BURST, FROST_RING, SAND_BLAST, TOON_FIRE, PIXEL_SPELL,
            EMBER_BURST, SHADOW_CLOAK, CHAIN_LIGHTNING, PLASMA_TRAIL, METEOR_STRIKE, OBSIDIAN, STAR_GATE, STEAM_VENT);

    private KilaPresets() {
    }
}
