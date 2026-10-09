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

    private HDRColor colorA = HDRColor.black();
    private HDRColor colorB = HDRColor.white();
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
        return colorA;
    }

    public HDRColor getColorB() {
        return colorB;
    }

    public void setColorA(HDRColor color) {
        this.colorA = color == null ? HDRColor.black() : color;
        syncPersistedFields();
    }

    public void setColorB(HDRColor color) {
        this.colorB = color == null ? HDRColor.white() : color;
        syncPersistedFields();
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
        HDRColorCompat.lerpPremultiplied(colorA.getR(), colorA.getG(), colorA.getB(), colorA.getIntensity(), colorA.getA(),
                colorB.getR(), colorB.getG(), colorB.getB(), colorB.getIntensity(), colorB.getA(), lerp.get(), out);
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
                && Objects.equals(colorB, other.colorB);
    }

    @Override
    public int hashCode() {
        return Objects.hash(colorA, colorB);
    }

    @Override
    public CompoundTag serializeNBT() {
        syncPersistedFields();
        var tag = new CompoundTag();
        PersistedParser.serializeNBT(tag, getClass(), this);
        return tag;
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
        if (colorA == null) colorA = HDRColor.black();
        if (colorB == null) colorB = HDRColor.white();
        colorA.set(redA, greenA, blueA, alphaA, intensityA);
        colorB.set(redB, greenB, blueB, alphaB, intensityB);
    }

    private void syncPersistedFields() {
        redA = colorA.getR();
        greenA = colorA.getG();
        blueA = colorA.getB();
        intensityA = colorA.getIntensity();
        alphaA = colorA.getA();
        redB = colorB.getR();
        greenB = colorB.getG();
        blueB = colorB.getB();
        intensityB = colorB.getIntensity();
        alphaB = colorB.getA();
    }
}
