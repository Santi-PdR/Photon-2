package com.lowdragmc.photon.client.gameobject.emitter.data.material.kila;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.MaterialContext;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import java.util.function.BiConsumer;

/** Rim light or rim fade from the angle to the view. A billboard always faces the camera, so this is for meshes. */
@OnlyIn(Dist.CLIENT)
public class KilaFresnel extends KilaModule {
    /** ⚠️ ordinals MIRRORED IN {@code kila_fresnel}. */
    public enum Mode {
        RIM, FADE_EDGES, FADE_CENTER
    }

    @Persisted
    public Mode mode = Mode.RIM;
    @Persisted
    public float power = 2;
    @Persisted
    public float scale = 1;
    @Persisted
    public HDRColor color = new HDRColor(0.4f, 0.8f, 1f, 1f, 2f);
    /** added to the view direction, so the rim leans as if lit from one side */
    @Persisted
    public Vector3f offset = new Vector3f();
    @Persisted(subPersisted = true)
    public final KilaDriver strength = new KilaDriver(1);

    public Mode effectiveMode() {
        return mode == null ? Mode.RIM : mode;
    }

    public HDRColor effectiveColor() {
        return color == null ? new HDRColor(0.4f, 0.8f, 1f, 1f, 2f) : color;
    }

    public Vector3f effectiveOffset() {
        return offset == null ? new Vector3f() : offset;
    }

    @Override
    public String id() {
        return "fresnel";
    }

    @Override
    public Category category() {
        return Category.LIGHTING;
    }

    @Override
    public void declare(KilaLayout.Builder layout) {
        layout.define("KILA_FRESNEL").vec4("KilaFresnel", 2, 1, 0, 0).vec4("KilaFresnelColor")
                .vec4("KilaFresnelOffset").params(KilaParam.FRESNEL_STRENGTH);
    }

    @Override
    public void apply(ShaderInstance shader, MaterialContext context, float[] scratch) {
        shader.safeGetUniform("KilaFresnel").set(
                finite(power, 2f, 0.01f, 16f), finite(scale, 1f, 0f, 16f),
                (float) effectiveMode().ordinal(), 0f);
        var rim = effectiveColor().toVector4fOpaque();
        shader.safeGetUniform("KilaFresnelColor").set(
                KilaMaterial.finite(rim.x), KilaMaterial.finite(rim.y), KilaMaterial.finite(rim.z), 1f);
        var safeOffset = effectiveOffset();
        shader.safeGetUniform("KilaFresnelOffset").set(
                KilaMaterial.finite(safeOffset.x), KilaMaterial.finite(safeOffset.y), KilaMaterial.finite(safeOffset.z), 0f);
    }

    @Override
    public void drivers(BiConsumer<KilaParam, KilaDriver> out) {
        out.accept(KilaParam.FRESNEL_STRENGTH, strength);
    }

    @Override
    public void buildModuleConfigurator(ConfiguratorGroup group) {
        KilaUI.choice(group, "kila.fresnel.mode", Mode.values(), this::effectiveMode, v -> mode = v);
        KilaUI.driver(group, "kila.fresnel.strength", strength, 0, 1);
        KilaUI.number(group, "kila.fresnel.power", () -> power, v -> power = v, 2, 0.01f, 16);
        KilaUI.number(group, "kila.fresnel.scale", () -> scale, v -> scale = v, 1, 0, 16);
        KilaUI.hdr(group, "kila.fresnel.color", this::effectiveColor, v -> color = v, false);
        KilaUI.vec3(group, "kila.fresnel.offset", this::effectiveOffset, v -> offset = v);
    }
}
