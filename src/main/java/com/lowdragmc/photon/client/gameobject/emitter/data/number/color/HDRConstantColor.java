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

    private HDRColor color = HDRColor.white();
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
        return color;
    }

    public void setColor(HDRColor color) {
        this.color = color == null ? HDRColor.white() : color;
        syncPersistedFields();
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
        HDRColorCompat.premultiplied(color.getR(), color.getG(), color.getB(),
                color.getIntensity(), color.getA(), out);
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
                && Objects.equals(color, other.color);
    }

    @Override
    public int hashCode() {
        return Objects.hash(color);
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
        if (!tag.contains("red", Tag.TAG_FLOAT) && tag.contains("color", Tag.TAG_COMPOUND)) {
            var oldColor = HDRColorCompat.fromLegacyTag(tag, "color", HDRColorCompat.white());
            red = oldColor.x;
            green = oldColor.y;
            blue = oldColor.z;
            intensity = oldColor.w;
        }
        color.set(red, green, blue, alpha, intensity);
    }

    private void syncPersistedFields() {
        red = color.getR();
        green = color.getG();
        blue = color.getB();
        intensity = color.getIntensity();
        alpha = color.getA();
    }
}
