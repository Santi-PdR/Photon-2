package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.photon.client.gameobject.emitter.data.ToggleGroup;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;

/**
 * Fades a fragment out as it approaches the opaque surface behind it, removing the hard line a quad shows
 * where it slices through the ground. Unity's Soft Particles / Unreal's DepthFade.
 *
 * <p>The GLSL half is {@code photon:soft_particle.glsl}, compiled into a material's fragment stage only
 * when {@link #isEnable()}. Texture and sprite materials add {@link #SHADER_DEFINE} to their shader
 * variant and bind scene depth from {@link RenderPassPipeline#getSceneSamplers()}.
 *
 * <p>Forge 1.20.1 has no modern render-pipeline material block, so the parameters are written to the
 * material's {@code ShaderInstance} before each draw. The depth sampler comes from the active Photon
 * render pass; editor thumbnails leave the fade disabled because they have no world depth behind them.
 */
public class SoftParticles extends ToggleGroup {
    public enum Fade {
        /** Scale the whole result — right for additive, and for the premultiplied late-composite layer. */
        BOTH,
        /** Scale alpha only — right for classic {@code SRC_ALPHA / ONE_MINUS_SRC_ALPHA}. */
        ALPHA_ONLY
    }

    @Configurable(name = "TextureMaterial.softParticles.distance")
    @ConfigNumber(range = {0.001, 64})
    public float distance = 1f;
    @Configurable(name = "TextureMaterial.softParticles.power")
    @ConfigNumber(range = {0.01, 16})
    public float power = 1f;
    @Configurable(name = "TextureMaterial.softParticles.fade")
    public Fade fade = Fade.BOTH;

    public void copyFrom(SoftParticles other) {
        setEnable(other.isEnable());
        distance = other.distance;
        power = other.power;
        fade = other.fade;
    }

    /**
     * The {@code SoftParticleParams} vec4 the fragment stage reads: fade distance in blocks, exponent,
     * {@code 1} to fade alpha only, and a trailing {@code 1} when it is on at all.
     *
     * <p>Disabled returns the neutral value rather than nothing, so a RenderType built from an OFF
     * material is still value-distinct from an ON one and the two never share a uniform buffer.</p>
     */
    public float[] params() {
        if (!isEnable()) {
            return new float[]{1f, 1f, 0f, 0f};
        }
        return new float[]{Math.max(distance, 1e-5f), Math.max(power, 1e-5f),
                fade == Fade.ALPHA_ONLY ? 1f : 0f, 1f};
    }

    /** Whether the current draw has a world scene behind it to fade against. */
    public boolean isActive(MaterialContext context) {
        var pipeline = RenderPassPipeline.getCurrent();
        return isEnable() && !context.isRenderingPreview() && pipeline != null
                && !pipeline.isWireframeSubPass();
    }
}
