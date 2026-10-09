package com.lowdragmc.photon.client.gameobject.emitter.data.number.color;

import com.lowdragmc.lowdraglib2.configurator.ui.HDRColorConfigurator;
import com.lowdragmc.lowdraglib2.math.HDRColor;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.configurator.NumberFunctionConfigurator;
import com.lowdragmc.photon.client.util.HDRColorCompat;
import org.joml.Vector4f;

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

    @Persisted
    private Vector4f color;
    /** Kept separate because 1.20.1's persisted Vector4f uses {@code w} for intensity. */
    @Persisted
    private float alpha = 1f;

    public HDRConstantColor() {
        this(HDRColor.white());
    }

    public HDRConstantColor(HDRColor color) {
        setColor(color);
    }

    /** Compatibility constructor for the port's earlier RGB/intensity vector API. */
    public HDRConstantColor(Vector4f color) {
        this.color = color == null ? HDRColorCompat.white() : new Vector4f(color);
        this.alpha = 1f;
    }

    public HDRColor getColor() {
        return HDRColorCompat.toHDRColor(color, alpha);
    }

    public void setColor(HDRColor color) {
        var safeColor = color == null ? HDRColor.white() : color;
        this.color = HDRColorCompat.toLegacyVector(safeColor);
        this.alpha = safeColor.getA();
    }

    /** Compatibility setter for the port's earlier RGB/intensity vector API. */
    public void setColor(Vector4f color) {
        this.color = color == null ? HDRColorCompat.white() : new Vector4f(color);
        this.alpha = 1f;
    }

    @Override
    public void loadConfig(NumberFunctionConfig config) {
        setColor(HDRColor.fromARGB((int) config.defaultValue()));
    }

    @Override
    public void sampleHDR(float t, Supplier<Float> lerp, Vector4f out) {
        HDRColorCompat.premultiplied(color, alpha, out);
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
                && Objects.equals(color, other.color) && Float.compare(alpha, other.alpha) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(color, alpha);
    }
}
