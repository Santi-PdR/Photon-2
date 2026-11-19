package com.lowdragmc.photon.client.gameobject.emitter.data.material.kila;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.MaterialContext;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector2f;

import java.util.function.BiConsumer;

/** Bends the mesh uv before any slot reads it: twirl, radial shear, wave and ripple. */
@OnlyIn(Dist.CLIENT)
public class KilaUvEffects extends KilaModule {
    public enum WaveAxis {
        /** the wave runs along u and bends v */
        U,
        V
    }

    /** radians of turn per uv unit from the centre */
    @Persisted(subPersisted = true)
    public final KilaDriver twirl = new KilaDriver(3);
    @Persisted
    public Vector2f twirlCenter = new Vector2f(0.5f, 0.5f);
    /** Unity's Radial Shear */
    @Persisted
    public Vector2f shear = new Vector2f(0, 0);
    @Persisted(subPersisted = true)
    public final KilaDriver waveAmplitude = new KilaDriver(0);
    /** waves per uv unit */
    @Persisted
    public float waveFrequency = 2;
    /** waves per second */
    @Persisted
    public float waveSpeed = 1;
    @Persisted
    public WaveAxis waveAxis = WaveAxis.U;
    /** rings running out from the centre; 0 = none */
    @Persisted
    public float rippleAmplitude;
    /** rings per uv unit */
    @Persisted
    public float rippleFrequency = 6;
    /** rings per second */
    @Persisted
    public float rippleSpeed = 1;

    public WaveAxis effectiveWaveAxis() {
        return waveAxis == null ? WaveAxis.U : waveAxis;
    }

    public Vector2f effectiveTwirlCenter() {
        return twirlCenter == null ? new Vector2f(0.5f, 0.5f) : twirlCenter;
    }

    public Vector2f effectiveShear() {
        return shear == null ? new Vector2f() : shear;
    }

    @Override
    public String id() {
        return "uv_effects";
    }

    @Override
    public Category category() {
        return Category.TEXTURE;
    }

    @Override
    public void declare(KilaLayout.Builder layout) {
        layout.define("KILA_UV_EFFECTS").float1("GameTime", 0).vec4("KilaTwirl", 0.5f, 0.5f, 0, 0).vec4("KilaShear")
                .vec4("KilaWave").vec4("KilaRipple").params(KilaParam.TWIRL_STRENGTH, KilaParam.WAVE_AMPLITUDE);
    }

    @Override
    public void apply(ShaderInstance shader, MaterialContext context, float[] scratch) {
        var center = effectiveTwirlCenter();
        var safeShear = effectiveShear();
        shader.safeGetUniform("KilaTwirl").set(
                finite(center.x, 0.5f, -100_000f, 100_000f), finite(center.y, 0.5f, -100_000f, 100_000f), 0f, 0f);
        shader.safeGetUniform("KilaShear").set(
                finite(safeShear.x, 0f, -100_000f, 100_000f), finite(safeShear.y, 0f, -100_000f, 100_000f), 0f, 0f);
        float tau = (float) (Math.PI * 2);
        shader.safeGetUniform("KilaWave").set(
                finite(waveFrequency, 2f, 0f, 64f) * tau, finite(waveSpeed, 1f, -64f, 64f) * tau,
                effectiveWaveAxis() == WaveAxis.U ? 1f : 0f, 0f);
        shader.safeGetUniform("KilaRipple").set(
                finite(rippleAmplitude, 0f, -1f, 1f), finite(rippleFrequency, 6f, 0f, 64f) * tau,
                finite(rippleSpeed, 1f, -64f, 64f) * tau, 0f);
    }

    @Override
    public void drivers(BiConsumer<KilaParam, KilaDriver> out) {
        out.accept(KilaParam.TWIRL_STRENGTH, twirl);
        out.accept(KilaParam.WAVE_AMPLITUDE, waveAmplitude);
    }

    @Override
    public void buildModuleConfigurator(ConfiguratorGroup group) {
        var swirl = KilaUI.subGroup(group, "kila.uv_effects.twirl_group", false);
        KilaUI.driver(swirl, "kila.uv_effects.twirl", twirl, -64, 64);
        KilaUI.vec2(swirl, "kila.uv_effects.twirl_center", this::effectiveTwirlCenter, v -> twirlCenter = v);
        KilaUI.vec2(swirl, "kila.uv_effects.shear", this::effectiveShear, v -> shear = v);
        var wave = KilaUI.subGroup(group, "kila.uv_effects.wave", false);
        KilaUI.driver(wave, "kila.uv_effects.wave_amplitude", waveAmplitude, -1, 1);
        KilaUI.number(wave, "kila.uv_effects.wave_frequency", () -> waveFrequency, v -> waveFrequency = v, 2, 0, 64);
        KilaUI.number(wave, "kila.uv_effects.wave_speed", () -> waveSpeed, v -> waveSpeed = v, 1, -64, 64);
        KilaUI.choice(wave, "kila.uv_effects.wave_axis", WaveAxis.values(), this::effectiveWaveAxis, v -> waveAxis = v);
        var ripple = KilaUI.subGroup(group, "kila.uv_effects.ripple", true);
        KilaUI.number(ripple, "kila.uv_effects.ripple_amplitude", () -> rippleAmplitude, v -> rippleAmplitude = v, 0, -1, 1);
        KilaUI.number(ripple, "kila.uv_effects.ripple_frequency", () -> rippleFrequency, v -> rippleFrequency = v, 6, 0, 64);
        KilaUI.number(ripple, "kila.uv_effects.ripple_speed", () -> rippleSpeed, v -> rippleSpeed = v, 1, -64, 64);
    }
}
