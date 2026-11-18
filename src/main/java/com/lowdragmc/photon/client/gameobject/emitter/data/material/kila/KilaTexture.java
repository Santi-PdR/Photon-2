package com.lowdragmc.photon.client.gameobject.emitter.data.material.kila;

import com.lowdragmc.kilagraph.rendertype.compiler.KGSamplerGl;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.util.Objects;

/** One texture a module samples, with the uv chain every slot runs (see {@code kila_uv} in {@code photon:kila_lib.glsl}). */
public class KilaTexture implements IPersistedSerializable {
    /** How a colour slot turns a texel into colour + alpha. ⚠️ ordinals MIRRORED IN {@code kila_color_mode}. */
    public enum ColorMode {
        RGBA,
        RED_AS_ALPHA,
        ALPHA_ONLY,
        LUMINANCE_AS_ALPHA
    }

    /** The one channel a scalar slot (mask, noise) reads. */
    public enum Channel {
        R, G, B, A, LUMINANCE;

        public void weights(float[] out) {
            out[0] = this == R ? 1 : this == LUMINANCE ? 0.2126f : 0;
            out[1] = this == G ? 1 : this == LUMINANCE ? 0.7152f : 0;
            out[2] = this == B ? 1 : this == LUMINANCE ? 0.0722f : 0;
            out[3] = this == A ? 1 : 0;
        }
    }

    public enum Wrap {
        REPEAT, CLAMP, MIRROR,
        /** Clamp, and transparent outside the texture — a slash or beam that slides off instead of smearing. */
        CLIP;

        int gl() {
            return switch (this) {
                case REPEAT -> GL11.GL_REPEAT;
                case MIRROR -> GL14.GL_MIRRORED_REPEAT;
                default -> GL12.GL_CLAMP_TO_EDGE;
            };
        }
    }

    /** Where the uv chain starts. A world plane tiles once per block, so a texture lines up across particles. */
    public enum UvSource {
        MESH(0), SCREEN(FLAG_SCREEN), WORLD_XZ(FLAG_WORLD_XZ), WORLD_XY(FLAG_WORLD_XY), WORLD_ZY(FLAG_WORLD_ZY);

        public final int flag;

        UvSource(int flag) {
            this.flag = flag;
        }

        public boolean isWorld() {
            return this == WORLD_XZ || this == WORLD_XY || this == WORLD_ZY;
        }
    }

    /** A noise computed in the shader instead of the texture: no sampler, nothing to tile. ⚠️ ordinals MIRRORED IN {@code kila_noise}. */
    public enum Noise {
        TEXTURE, SIMPLE, GRADIENT, VORONOI
    }

    // ⚠️ MIRRORED IN photon:kila_lib.glsl (KILA_UV_*)
    public static final int FLAG_POLAR = 1;
    public static final int FLAG_SCREEN = 2;
    public static final int FLAG_CLIP_U = 4;
    public static final int FLAG_CLIP_V = 8;
    public static final int FLAG_WORLD_XZ = 16;
    public static final int FLAG_WORLD_XY = 32;
    public static final int FLAG_WORLD_ZY = 64;

    @Persisted
    public ResourceLocation texture;
    @Persisted
    public ColorMode colorMode = ColorMode.RGBA;
    @Persisted
    public Channel channel = Channel.R;
    @Persisted
    public Vector2f tiling = new Vector2f(1, 1);
    @Persisted
    public Vector2f offset = new Vector2f(0, 0);
    /** uv per second */
    @Persisted
    public Vector2f scroll = new Vector2f(0, 0);
    /** degrees about the uv centre */
    @Persisted
    public float rotation;
    /** degrees per second */
    @Persisted
    public float rotationSpeed;
    @Persisted
    public boolean polar;
    @Persisted
    public Vector2f polarCenter = new Vector2f(0.5f, 0.5f);
    @Persisted
    public float polarRadial = 1;
    @Persisted
    public float polarAngular = 1;
    @Persisted
    public UvSource uvSource = UvSource.MESH;
    @Persisted
    public Noise noise = Noise.TEXTURE;
    /** cells across one uv unit */
    @Persisted
    public float noiseScale = 8;
    /** how fast voronoi cells drift */
    @Persisted
    public float noiseMotion = 1;
    @Persisted
    public Wrap wrapU = Wrap.REPEAT;
    @Persisted
    public Wrap wrapV = Wrap.REPEAT;
    @Persisted
    public boolean nearest;

    public KilaTexture() {
        this(KilaTextures.WHITE);
    }

    public KilaTexture(ResourceLocation texture) {
        this.texture = texture;
    }

    public ResourceLocation getTexture() {
        if (texture == null) texture = KilaTextures.WHITE;
        return texture;
    }

    /** Repair fields that would otherwise make their configurator dereference null or non-finite values. */
    public void sanitizeForEditor() {
        getTexture();
        if (colorMode == null) colorMode = ColorMode.RGBA;
        if (channel == null) channel = Channel.R;
        if (uvSource == null) uvSource = UvSource.MESH;
        if (noise == null) noise = Noise.TEXTURE;
        if (wrapU == null) wrapU = Wrap.REPEAT;
        if (wrapV == null) wrapV = Wrap.REPEAT;
        if (tiling == null) tiling = new Vector2f(1, 1);
        if (offset == null) offset = new Vector2f();
        if (scroll == null) scroll = new Vector2f();
        if (polarCenter == null) polarCenter = new Vector2f(0.5f, 0.5f);
        tiling.set(finite(tiling.x, 1, 100_000), finite(tiling.y, 1, 100_000));
        offset.set(finite(offset.x, 0, 100_000), finite(offset.y, 0, 100_000));
        scroll.set(finite(scroll.x, 0, 100_000), finite(scroll.y, 0, 100_000));
        polarCenter.set(finite(polarCenter.x, 0.5f, 100_000), finite(polarCenter.y, 0.5f, 100_000));
        rotation = finite(rotation, 0, 1_000_000);
        rotationSpeed = finite(rotationSpeed, 0, 1_000_000);
        polarRadial = finite(polarRadial, 1, 100_000);
        polarAngular = finite(polarAngular, 1, 100_000);
        noiseScale = finite(noiseScale, 8, 10_000);
        noiseMotion = finite(noiseMotion, 1, 10_000);
    }

    public UvSource effectiveUvSource() {
        return uvSource == null ? UvSource.MESH : uvSource;
    }

    public Noise effectiveNoise() {
        return noise == null ? Noise.TEXTURE : noise;
    }

    public ColorMode effectiveColorMode() {
        return colorMode == null ? ColorMode.RGBA : colorMode;
    }

    public Channel effectiveChannel() {
        return channel == null ? Channel.R : channel;
    }

    public Wrap effectiveWrapU() {
        return wrapU == null ? Wrap.REPEAT : wrapU;
    }

    public Wrap effectiveWrapV() {
        return wrapV == null ? Wrap.REPEAT : wrapV;
    }

    /** Safe authoring vectors for export paths, which build node defaults rather than upload uniforms. */
    public Vector2f effectiveTiling() {
        return new Vector2f(finite(tiling == null ? 1 : tiling.x, 1, 100_000),
                finite(tiling == null ? 1 : tiling.y, 1, 100_000));
    }

    public Vector2f effectiveOffset() {
        return new Vector2f(finite(offset == null ? 0 : offset.x, 0, 100_000),
                finite(offset == null ? 0 : offset.y, 0, 100_000));
    }

    public Vector2f effectiveScroll() {
        return new Vector2f(finite(scroll == null ? 0 : scroll.x, 0, 100_000),
                finite(scroll == null ? 0 : scroll.y, 0, 100_000));
    }

    public Vector2f effectivePolarCenter() {
        return new Vector2f(finite(polarCenter == null ? 0.5f : polarCenter.x, 0.5f, 100_000),
                finite(polarCenter == null ? 0.5f : polarCenter.y, 0.5f, 100_000));
    }

    public float effectiveRotation() {
        return finite(rotation, 0, 1_000_000);
    }

    public float effectiveRotationSpeed() {
        return finite(rotationSpeed, 0, 1_000_000);
    }

    public float effectivePolarRadial() {
        return finite(polarRadial, 1, 100_000);
    }

    public float effectivePolarAngular() {
        return finite(polarAngular, 1, 100_000);
    }

    public float effectiveNoiseScale() {
        return finite(noiseScale, 8, 10_000);
    }

    public float effectiveNoiseMotion() {
        return finite(noiseMotion, 1, 10_000);
    }

    private static float finite(float value, float fallback, float limit) {
        if (!Float.isFinite(value)) return fallback;
        return Math.max(-limit, Math.min(limit, value));
    }

    public int flags() {
        int flags = 0;
        if (polar) flags |= FLAG_POLAR;
        flags |= effectiveUvSource().flag;
        if (effectiveWrapU() == Wrap.CLIP) flags |= FLAG_CLIP_U;
        if (effectiveWrapV() == Wrap.CLIP) flags |= FLAG_CLIP_V;
        return flags;
    }

    /** Wrap inside one flipbook cell, u in bits 0-1 and v in 2-3: 0 repeat, 1 clamp, 2 mirror. */
    public int cellWrap() {
        return cellWrap(effectiveWrapU()) | cellWrap(effectiveWrapV()) << 2;
    }

    private static int cellWrap(Wrap wrap) {
        return switch (wrap) {
            case REPEAT -> 0;
            case MIRROR -> 2;
            default -> 1;
        };
    }

    public boolean isProcedural() {
        return effectiveNoise() != Noise.TEXTURE;
    }

    public boolean usesWorld() {
        return effectiveUvSource().isWorld();
    }

    /** Upload {@code tiling.xy} and {@code offset.zw} to an already named vec4 uniform. */
    public void uploadTilingOffset(ShaderInstance shader, String uniform) {
        shader.safeGetUniform(uniform).set(finite(tiling == null ? 1 : tiling.x, 1, 100_000),
                finite(tiling == null ? 1 : tiling.y, 1, 100_000), finite(offset == null ? 0 : offset.x, 0, 100_000),
                finite(offset == null ? 0 : offset.y, 0, 100_000));
    }

    /** Uploads {@code <prefix>ST}, {@code <prefix>Anim} and {@code <prefix>Polar}. */
    public void uploadUv(ShaderInstance shader, String prefix) {
        uploadTilingOffset(shader, prefix + "ST");
        shader.safeGetUniform(prefix + "Anim").set(finite(scroll == null ? 0 : scroll.x, 0, 100_000),
                finite(scroll == null ? 0 : scroll.y, 0, 100_000),
                (float) Math.toRadians(finite(rotation, 0, 1_000_000)),
                (float) Math.toRadians(finite(rotationSpeed, 0, 1_000_000)));
        shader.safeGetUniform(prefix + "Polar").set(finite(polarCenter == null ? 0.5f : polarCenter.x, 0.5f, 100_000),
                finite(polarCenter == null ? 0.5f : polarCenter.y, 0.5f, 100_000), finite(polarRadial, 1, 100_000),
                finite(polarAngular, 1, 100_000));
    }

    public void uploadChannel(ShaderInstance shader, String name, float[] scratch) {
        effectiveChannel().weights(scratch);
        shader.safeGetUniform(name).set(scratch[0], scratch[1], scratch[2], scratch[3]);
    }

    /** {@code <prefix>Noise} for a procedural slot, else {@code <prefix>Channel}. */
    public void uploadSource(ShaderInstance shader, String prefix, float[] scratch) {
        if (isProcedural()) {
            shader.safeGetUniform(prefix + "Noise").set(effectiveNoise().ordinal(),
                    effectiveNoiseScale(), effectiveNoiseMotion(), 0f);
        } else {
            uploadChannel(shader, prefix + "Channel", scratch);
        }
    }

    public KGSamplerGl.GlSampler glSampler() {
        int filter = nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        return new KGSamplerGl.GlSampler(filter, filter, effectiveWrapU().gl(), effectiveWrapV().gl());
    }

    /** Two slots with equal keys can share one sampler. */
    public boolean sameSampler(KilaTexture other) {
        return other != null && Objects.equals(getTexture(), other.getTexture()) && nearest == other.nearest
                && effectiveWrapU().gl() == other.effectiveWrapU().gl()
                && effectiveWrapV().gl() == other.effectiveWrapV().gl();
    }

    /** What of this slot changes the program variant. Allocation-free: it runs on every draw. */
    public int structureHash() {
        int hash = (isProcedural() ? 1 : 0) | (usesWorld() ? 2 : 0);
        if (isProcedural()) return hash;
        hash = hash * 31 + getTexture().hashCode();
        hash = hash * 31 + (nearest ? 1 : 0);
        hash = hash * 31 + effectiveWrapU().gl();
        return hash * 31 + effectiveWrapV().gl();
    }

    public KilaTexture tiling(float u, float v) {
        tiling.set(u, v);
        return this;
    }

    public KilaTexture scroll(float u, float v) {
        scroll.set(u, v);
        return this;
    }

    public KilaTexture noise(Noise noise, float scale) {
        this.noise = noise;
        noiseScale = scale;
        return this;
    }

    public KilaTexture wrap(Wrap u, Wrap v) {
        wrapU = u;
        wrapV = v;
        return this;
    }
}
