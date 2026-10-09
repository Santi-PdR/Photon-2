package com.lowdragmc.photon.client.gameobject.emitter.data.number.color;

import com.lowdragmc.lowdraglib2.configurator.ui.HDRColorConfigurator;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.configurator.NumberFunctionConfigurator;
import com.lowdragmc.photon.client.util.HDRColorCompat;
import com.lowdragmc.lowdraglib.gui.editor.runtime.PersistedParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The HDR counterpart of {@link Color}, using LDLib2's RGBA + intensity color model.
 *
 * <p>Deliberately not derived from {@code Constant} — its {@code @Persisted Number} can't hold an HDR
 * colour, and inheriting it would also make this droppable onto every scalar field (the drag predicate
 * keys off the configurator's default value type, which is a {@code Constant}).
 */
@LDLRegisterClient(name = "hdr_color", registry = "photon:number_function")
public class HDRConstantColor implements HDRColorFunction {

    @Persisted private float red = 1f;
    @Persisted private float green = 1f;
    @Persisted private float blue = 1f;
    @Persisted private float intensity = 1f;
    @Persisted private float alpha = 1f;

    public HDRConstantColor() {
        this(HDRColor.white());
    }

    public HDRConstantColor(HDRColor color) {
        setColor(color);
    }

    /** Compatibility constructor for the port's earlier RGB/intensity vector API. */
    public HDRConstantColor(Vector4f color) {
        this(HDRColorCompat.toHDRColor(color == null ? HDRColorCompat.white() : color, 1f));
    }

    public HDRColor getColor() {
        return new HDRColor(red, green, blue, alpha, intensity);
    }

    public void setColor(HDRColor color) {
        var safeColor = color == null ? HDRColor.white() : color;
        this.red = safeColor.getR();
        this.green = safeColor.getG();
        this.blue = safeColor.getB();
        this.intensity = safeColor.getIntensity();
        this.alpha = safeColor.getA();
    }

    /** Compatibility setter for the port's earlier RGB/intensity vector API. */
    public void setColor(Vector4f color) {
        setColor(HDRColorCompat.toHDRColor(color == null ? HDRColorCompat.white() : color, 1f));
    }

    @Override
    public void loadConfig(NumberFunctionConfig config) {
        setColor(HDRColor.fromARGB((int) config.defaultValue()));
    }

    @Override
    public void sampleHDR(float t, Supplier<Float> lerp, Vector4f out) {
        HDRColorCompat.premultiplied(red, green, blue, intensity, alpha, out);
    }

    @Override
    public NumberFunction copy() {
        return new HDRConstantColor(getColor().copy());
    }

    @Override
    public void createConfigurator(NumberFunctionConfigurator configurator) {
        configurator.inlineContainer.addChildren(new HDRColorConfigurator("", this::getColor, hdr -> {
            setColor(hdr);
            configurator.updateValue(this);
        }, getColor(), true));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        return obj instanceof HDRConstantColor other
                && Float.compare(red, other.red) == 0
                && Float.compare(green, other.green) == 0
                && Float.compare(blue, other.blue) == 0
                && Float.compare(intensity, other.intensity) == 0
                && Float.compare(alpha, other.alpha) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(red, green, blue, intensity, alpha);
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        PersistedParser.deserializeNBT(tag, new HashMap<>(), getClass(), this);
        if (!tag.contains("red", Tag.TAG_FLOAT) && tag.contains("color", Tag.TAG_COMPOUND)) {
            var oldColor = HDRColorCompat.fromLegacyTag(tag, "color", HDRColorCompat.white());
            red = oldColor.x;
            green = oldColor.y;
            blue = oldColor.z;
            intensity = oldColor.w;
        }
    }
}
