package com.lowdragmc.photon.client.gameobject.emitter.data.number.color;

import com.lowdragmc.lowdraglib2.configurator.ui.HDRColorConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.gui.editor.runtime.PersistedParser;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.configurator.NumberFunctionConfigurator;
import com.lowdragmc.photon.client.util.HDRColorCompat;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The HDR counterpart of {@link RandomColor}: a random blend between two HDR colors. The blend
 * happens in float space (no ARGB round-trip), so alpha and intensities above 1 survive it.
 */
@LDLRegisterClient(name = "hdr_random_color", registry = "photon:number_function")
public class HDRRandomColor implements HDRColorFunction {

    @Persisted private float redA;
    @Persisted private float greenA;
    @Persisted private float blueA;
    @Persisted private float intensityA = 1f;
    @Persisted private float alphaA = 1f;
    @Persisted private float redB = 1f;
    @Persisted private float greenB = 1f;
    @Persisted private float blueB = 1f;
    @Persisted private float intensityB = 1f;
    @Persisted private float alphaB = 1f;

    public HDRRandomColor() {
        this(HDRColor.black(), HDRColor.white());
    }

    public HDRRandomColor(HDRColor colorA, HDRColor colorB) {
        setColorA(colorA);
        setColorB(colorB);
    }

    /** Compatibility constructor for the port's earlier RGB/intensity vector API. */
    public HDRRandomColor(Vector4f colorA, Vector4f colorB) {
        setColorA(HDRColorCompat.toHDRColor(colorA == null ? HDRColorCompat.black() : colorA, 1f));
        setColorB(HDRColorCompat.toHDRColor(colorB == null ? HDRColorCompat.white() : colorB, 1f));
    }

    public HDRColor getColorA() {
        return new HDRColor(redA, greenA, blueA, alphaA, intensityA);
    }

    public HDRColor getColorB() {
        return new HDRColor(redB, greenB, blueB, alphaB, intensityB);
    }

    public void setColorA(HDRColor color) {
        var safeColor = color == null ? HDRColor.black() : color;
        this.redA = safeColor.getR();
        this.greenA = safeColor.getG();
        this.blueA = safeColor.getB();
        this.intensityA = safeColor.getIntensity();
        this.alphaA = safeColor.getA();
    }

    public void setColorB(HDRColor color) {
        var safeColor = color == null ? HDRColor.white() : color;
        this.redB = safeColor.getR();
        this.greenB = safeColor.getG();
        this.blueB = safeColor.getB();
        this.intensityB = safeColor.getIntensity();
        this.alphaB = safeColor.getA();
    }

    /** Compatibility setters for the port's earlier RGB/intensity vector API. */
    public void setColorA(Vector4f color) {
        setColorA(HDRColorCompat.toHDRColor(color == null ? HDRColorCompat.black() : color, 1f));
    }

    public void setColorB(Vector4f color) {
        setColorB(HDRColorCompat.toHDRColor(color == null ? HDRColorCompat.white() : color, 1f));
    }

    @Override
    public void loadConfig(NumberFunctionConfig config) {
        var color = (int) config.defaultValue();
        setColorA(HDRColor.fromARGB(color));
        setColorB(HDRColor.fromARGB(color));
    }

    @Override
    public void sampleHDR(float t, Supplier<Float> lerp, Vector4f out) {
        HDRColorCompat.lerpPremultiplied(redA, greenA, blueA, intensityA, alphaA,
                redB, greenB, blueB, intensityB, alphaB, lerp.get(), out);
    }

    @Override
    public NumberFunction copy() {
        return new HDRRandomColor(getColorA().copy(), getColorB().copy());
    }

    @Override
    public void createConfigurator(NumberFunctionConfigurator configurator) {
        HDRColorConfigurator a, b;
        configurator.inlineContainer.addChild(new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.gapAll(2);
            layout.marginLeft(2);
            layout.flexDirection(FlexDirection.ROW);
            layout.wrap(FlexWrap.WRAP);
        }).addChildren(
                a = new HDRColorConfigurator("", this::getColorA, color -> {
                    setColorA(color);
                    configurator.updateValue(this);
                }, getColorA(), true),
                b = new HDRColorConfigurator("", this::getColorB, color -> {
                    setColorB(color);
                    configurator.updateValue(this);
                }, getColorB(), true)
        ));
        a.layout(layout -> {
            layout.flex(1);
            layout.minWidth(40);
            layout.height(14);
        });
        b.layout(layout -> {
            layout.flex(1);
            layout.minWidth(40);
            layout.height(14);
        });
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        return obj instanceof HDRRandomColor other
                && Float.compare(redA, other.redA) == 0
                && Float.compare(greenA, other.greenA) == 0
                && Float.compare(blueA, other.blueA) == 0
                && Float.compare(intensityA, other.intensityA) == 0
                && Float.compare(alphaA, other.alphaA) == 0
                && Float.compare(redB, other.redB) == 0
                && Float.compare(greenB, other.greenB) == 0
                && Float.compare(blueB, other.blueB) == 0
                && Float.compare(intensityB, other.intensityB) == 0
                && Float.compare(alphaB, other.alphaB) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(redA, greenA, blueA, intensityA, alphaA,
                redB, greenB, blueB, intensityB, alphaB);
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        PersistedParser.deserializeNBT(tag, new HashMap<>(), getClass(), this);
        if (!tag.contains("redA", Tag.TAG_FLOAT) && tag.contains("colorA", Tag.TAG_COMPOUND)) {
            var oldColor = HDRColorCompat.fromLegacyTag(tag, "colorA", HDRColorCompat.black());
            redA = oldColor.x;
            greenA = oldColor.y;
            blueA = oldColor.z;
            intensityA = oldColor.w;
        }
        if (!tag.contains("redB", Tag.TAG_FLOAT) && tag.contains("colorB", Tag.TAG_COMPOUND)) {
            var oldColor = HDRColorCompat.fromLegacyTag(tag, "colorB", HDRColorCompat.white());
            redB = oldColor.x;
            greenB = oldColor.y;
            blueB = oldColor.z;
            intensityB = oldColor.w;
        }
    }
}
