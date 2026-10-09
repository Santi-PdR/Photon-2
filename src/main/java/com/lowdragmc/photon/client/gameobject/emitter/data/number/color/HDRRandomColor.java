package com.lowdragmc.photon.client.gameobject.emitter.data.number.color;

import com.lowdragmc.lowdraglib2.configurator.ui.HDRColorConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.configurator.NumberFunctionConfigurator;
import com.lowdragmc.photon.client.util.HDRColorCompat;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import org.joml.Vector4f;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The HDR counterpart of {@link RandomColor}: a random blend between two HDR colors. The blend
 * happens in float space (no ARGB round-trip), so alpha and intensities above 1 survive it.
 */
@LDLRegisterClient(name = "hdr_random_color", registry = "photon:number_function")
public class HDRRandomColor implements HDRColorFunction {

    @Persisted
    private Vector4f colorA;
    @Persisted
    private Vector4f colorB;
    /** Kept separate because 1.20.1's persisted Vector4f uses {@code w} for intensity. */
    @Persisted
    private float alphaA = 1f;
    @Persisted
    private float alphaB = 1f;

    public HDRRandomColor() {
        this(HDRColor.black(), HDRColor.white());
    }

    public HDRRandomColor(HDRColor colorA, HDRColor colorB) {
        setColorA(colorA);
        setColorB(colorB);
    }

    /** Compatibility constructor for the port's earlier RGB/intensity vector API. */
    public HDRRandomColor(Vector4f colorA, Vector4f colorB) {
        this.colorA = colorA == null ? HDRColorCompat.black() : new Vector4f(colorA);
        this.colorB = colorB == null ? HDRColorCompat.white() : new Vector4f(colorB);
        this.alphaA = 1f;
        this.alphaB = 1f;
    }

    public HDRColor getColorA() {
        return HDRColorCompat.toHDRColor(colorA, alphaA);
    }

    public HDRColor getColorB() {
        return HDRColorCompat.toHDRColor(colorB, alphaB);
    }

    public void setColorA(HDRColor color) {
        var safeColor = color == null ? HDRColor.black() : color;
        this.colorA = HDRColorCompat.toLegacyVector(safeColor);
        this.alphaA = safeColor.getA();
    }

    public void setColorB(HDRColor color) {
        var safeColor = color == null ? HDRColor.white() : color;
        this.colorB = HDRColorCompat.toLegacyVector(safeColor);
        this.alphaB = safeColor.getA();
    }

    /** Compatibility setters for the port's earlier RGB/intensity vector API. */
    public void setColorA(Vector4f color) {
        this.colorA = color == null ? HDRColorCompat.black() : new Vector4f(color);
        this.alphaA = 1f;
    }

    public void setColorB(Vector4f color) {
        this.colorB = color == null ? HDRColorCompat.white() : new Vector4f(color);
        this.alphaB = 1f;
    }

    @Override
    public void loadConfig(NumberFunctionConfig config) {
        var color = (int) config.defaultValue();
        setColorA(HDRColor.fromARGB(color));
        setColorB(HDRColor.fromARGB(color));
    }

    @Override
    public void sampleHDR(float t, Supplier<Float> lerp, Vector4f out) {
        HDRColorCompat.lerpPremultiplied(colorA, alphaA, colorB, alphaB, lerp.get(), out);
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
                && Objects.equals(colorA, other.colorA)
                && Objects.equals(colorB, other.colorB)
                && Float.compare(alphaA, other.alphaA) == 0
                && Float.compare(alphaB, other.alphaB) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(colorA, colorB, alphaA, alphaB);
    }
}
