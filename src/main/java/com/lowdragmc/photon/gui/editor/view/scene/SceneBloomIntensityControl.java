package com.lowdragmc.photon.gui.editor.view.scene;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.client.PhotonIcons;
import com.lowdragmc.photon.client.fx.FXData;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * The scene bar's bloom-intensity knob for <b>this FX</b>: the bloom icon, a slider and the value
 * it writes.
 * <p>
 * Bloom strength is authored on the effect ({@link FXData#bloomIntensity()}), not a client-wide
 * config. The slider follows the current project's FX, so two effects can glow differently and a
 * reload of the project brings the authored value back. Bloom on/off is the toggle beside it
 * ({@link FXData#bloomEnabled()}); this only changes how strong the halo is.
 * <p>
 * The slider is a {@link ContinuousSlider}, not a {@code Scroller}: a slider is dragged from anywhere on its
 * track and follows the pointer, which is what a scrollbar-shaped control never did here.
 */
@OnlyIn(Dist.CLIENT)
public class SceneBloomIntensityControl extends UIElement {
    public static final float MIN = FXData.MIN_BLOOM_INTENSITY;
    public static final float MAX = FXData.MAX_BLOOM_INTENSITY;
    /** Two decimals, the readout's resolution; the slider snaps to it so the two never disagree. */
    public static final float STEP = 0.01f;

    public final ContinuousSlider slider;
    public final Label readout = new Label();
    private final Supplier<FXData> data;
    private final FXData fallback = new FXData();

    public SceneBloomIntensityControl() {
        this(() -> null);
    }

    public SceneBloomIntensityControl(Supplier<FXData> data) {
        this.data = data == null ? () -> null : data;
        slider = new ContinuousSlider(MIN, MAX, current());
        layout(layout -> {
            layout.heightPercent(100);
            layout.width(64);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(1);
        });
        style(style -> style.tooltips(
                "photon.fx.bloom.intensity",
                "photon.fx.bloom.intensity.tooltip"));

        slider.step(STEP).onValueChanged(this::setIntensity);
        slider.layout(layout -> {
            layout.heightPercent(100);
            layout.width(40);
            layout.flex(1);
        });
        slider.style(style -> style.tooltips(
                "photon.fx.bloom.intensity",
                "photon.fx.bloom.intensity.tooltip"));

        readout.textStyle(style -> style
                .textAlignVertical(Vertical.CENTER)
                .textAlignHorizontal(Horizontal.CENTER)
                .adaptiveWidth(true));
        readout.layout(layout -> {
            layout.heightPercent(100);
            layout.width(20);
        });
        readout.setText(readoutText(slider.getValue()));

        addEventListener(UIEvents.TICK, event -> {
            // while the pointer holds it, the slider is the truth: writing the FX back would fight the drag
            if (slider.isDragging()) {
                readout.setText(readoutText(slider.getValue()));
                return;
            }
            var value = current();
            if (Math.abs(slider.getValue() - value) > STEP / 2f) {
                slider.setValue(value, false);
            }
            readout.setText(readoutText(slider.getValue()));
        });

        var icon = new UIElement().layout(layout -> {
            layout.heightPercent(100);
            layout.setAspectRatio(1);
        }).style(style -> style.backgroundTexture(PhotonIcons.BLOOM));
        icon.setAllowHitTest(false);

        addChildren(icon, slider, readout);
    }

    private FXData data() {
        var current = data.get();
        return current == null ? fallback : current;
    }

    /** The intensity this FX holds right now. */
    public float current() {
        return data().bloomIntensity();
    }

    private void setIntensity(float value) {
        data().setBloomIntensity(value);
        readout.setText(readoutText(value));
    }

    /** The text the readout shows, exposed so the smoke test can assert on it. */
    public static Component readoutText(float value) {
        return Component.literal(String.format(Locale.ROOT, "%.2f", value));
    }
}
