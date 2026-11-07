package com.lowdragmc.photon.gui.editor.view.scene;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.PhotonConfig;
import com.lowdragmc.photon.client.PhotonIcons;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * The scene bar's bloom-intensity knob: the bloom icon, a slider and the value it writes, next to the bloom
 * toggle.
 * <p>
 * It shows {@link PhotonConfig#bloomIntensity} rather than keeping a copy: the value is read back every tick, so
 * the slider follows the config screen and a config reload. Dragging it makes the same edit the config screen
 * makes — the shipped default (and the config file's own default) is never touched, only the value the author
 * picks. Bloom stays on/off through the toggle beside it; this only changes how strong the halo is.
 */
@OnlyIn(Dist.CLIENT)
public class SceneBloomIntensityControl extends UIElement {
    /** The slider's span, kept in step with {@code bloom_intensity}'s {@code defineInRange(0, 2)}. */
    public static final float MIN = 0f;
    public static final float MAX = 2f;

    public final Scroller.Horizontal slider = new Scroller.Horizontal();
    public final Label readout = new Label();

    public SceneBloomIntensityControl() {
        layout(layout -> {
            layout.heightPercent(100);
            layout.width(64);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(1);
        });
        style(style -> style.tooltips(
                "photon.configuration.bloom_intensity_in_scene",
                "photon.configuration.bloom_intensity_in_scene.tooltip"));

        // the bar itself is the handle, with no arrows, like the scene-range scroller beside it
        slider.headButton.setDisplay(false);
        slider.tailButton.setDisplay(false);
        slider.setRange(MIN, MAX).setScrollBarSize(10);
        slider.setValue(current(), false);
        slider.setOnValueChanged(this::setIntensity);
        slider.layout(layout -> {
            layout.heightPercent(100);
            layout.flex(1);
        });
        // the whole control carries the tooltip; the slider is what the pointer actually lands on
        slider.style(style -> style.tooltips(
                "photon.configuration.bloom_intensity_in_scene",
                "photon.configuration.bloom_intensity_in_scene.tooltip"));

        readout.textStyle(style -> style
                .textAlignVertical(Vertical.CENTER)
                .textAlignHorizontal(Horizontal.CENTER)
                .adaptiveWidth(true));
        readout.layout(layout -> {
            layout.heightPercent(100);
            layout.width(20);
        });
        readout.setText(readoutText(current()));

        addEventListener(UIEvents.TICK, event -> {
            if (slider.isDragging()) return;
            var value = current();
            if (Math.abs(slider.getValue() - value) > 0.0005f) {
                slider.setValue(value, false);
            }
            readout.setText(readoutText(value));
        });

        var icon = new UIElement().layout(layout -> {
            layout.heightPercent(100);
            layout.setAspectRatio(1);
        }).style(style -> style.backgroundTexture(PhotonIcons.BLOOM));
        // the icon is a label for the slider, not a target: it must not block the click
        icon.setAllowHitTest(false);

        addChildren(icon, slider, readout);
    }

    /** The intensity the config holds right now. */
    public static float current() {
        return PhotonConfig.INSTANCE.bloomIntensity.get().floatValue();
    }

    private void setIntensity(float value) {
        var clamped = Mth.clamp(value, MIN, MAX);
        PhotonConfig.INSTANCE.bloomIntensity.set((double) clamped);
        readout.setText(readoutText(clamped));
    }

    private static Component readoutText(float value) {
        // two decimals: the useful resolution of a 0..2 halo strength
        return Component.literal(String.format(Locale.ROOT, "%.2f", value));
    }
}
