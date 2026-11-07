package com.lowdragmc.photon.gui.editor.view.scene;

import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * A horizontal slider that behaves like one: pressing anywhere on the track moves the handle there, and from
 * then on the handle follows the pointer until the button is released — including past the ends of the track.
 * <p>
 * This replaces {@code Scroller.Horizontal} where a value is edited rather than scrolled. A scroller's handle is
 * its own drag source: a press on the track is only a click that repositions it, and the pointer has to land on
 * the handle again to move it, which is why the bloom knob felt like "it clicks, it does not slide".
 * <p>
 * The track is drawn here instead of being an element tree: two rectangles and a handle, at this element's own
 * content box, so nothing between the pointer and this element can swallow a press. The value is the truth; the
 * pixels follow it.
 */
@OnlyIn(Dist.CLIENT)
public class ContinuousSlider extends UIElement {
    private static final float TRACK_HEIGHT = 4f;
    private static final float HANDLE_WIDTH = 4f;
    private static final int TRACK_COLOR = ColorPattern.DARK_GRAY.color;
    private static final int FILL_COLOR = ColorPattern.GRAY.color;
    private static final int HANDLE_COLOR = ColorPattern.LIGHT_GRAY.color;
    private static final int HANDLE_HOVER_COLOR = ColorPattern.WHITE.color;

    private float min;
    private float max;
    private float value;
    private float step = 0f;
    private boolean dragging;
    private boolean hovered;
    private Consumer<Float> onValueChanged;
    /**
     * The track's width to use while this element is not laid out, which only happens headless (a smoke test
     * dispatching pointer events at a widget that was never mounted). A mounted slider always uses its own box.
     */
    private float unmountedTrackWidth = 0f;

    public ContinuousSlider(float min, float max, float value) {
        this.min = min;
        this.max = Math.max(min, max);
        this.value = Mth.clamp(value, this.min, this.max);
        layout(layout -> {
            layout.heightPercent(100);
            layout.widthPercent(100);
        });
        setAllowHitTest(true);

        // the press starts the drag wherever it lands, and the handle jumps there first: a click is an exact set
        addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0) return;
            dragging = true;
            applyPointer(event.x);
            event.stopPropagation();
        });
        addEventListener(UIEvents.MOUSE_MOVE, event -> {
            if (!dragging) return;
            applyPointer(event.x);
            event.stopPropagation();
        });
        // the release lands on whatever is under the pointer, which may be outside this element, so the drag is
        // also followed from the tick: the pointer's own position every frame plus the button's real state is
        // what keeps a slider from sticking when the movie ends off the bar
        addEventListener(UIEvents.TICK, event -> {
            if (!dragging) return;
            if (!isMouseDown(0)) {
                dragging = false;
                return;
            }
            applyPointer(pointerX());
        });
        addEventListener(UIEvents.MOUSE_UP, event -> dragging = false);
        addEventListener(UIEvents.MOUSE_ENTER, event -> hovered = true);
        addEventListener(UIEvents.MOUSE_LEAVE, event -> hovered = false);
    }

    public ContinuousSlider onValueChanged(Consumer<Float> listener) {
        this.onValueChanged = listener;
        return this;
    }

    /** A quantisation for the value, e.g. 0.01 for two decimals; 0 keeps it continuous. */
    public ContinuousSlider step(float step) {
        this.step = Math.max(0f, step);
        return this;
    }

    public float getValue() {
        return value;
    }

    public float getMin() {
        return min;
    }

    public float getMax() {
        return max;
    }

    /** Whether the pointer is holding the handle, so an owner can leave the value alone while it does. */
    public boolean isDragging() {
        return dragging;
    }

    /** Writes the value; {@code notify} tells the owner when the change came from the pointer. */
    public ContinuousSlider setValue(float value, boolean notify) {
        var next = Mth.clamp(value, min, max);
        if (step > 0) next = Mth.clamp(Math.round(next / step) * step, min, max);
        if (Math.abs(next - this.value) < 1e-6f) return this;
        this.value = next;
        if (notify && onValueChanged != null) onValueChanged.accept(next);
        return this;
    }

    /** Test seam: a track width for a slider that is never mounted, so its drag path stays covered. */
    public ContinuousSlider setUnmountedTrackWidth(float width) {
        this.unmountedTrackWidth = Math.max(0f, width);
        return this;
    }

    public void setRange(float min, float max) {
        this.min = min;
        this.max = Math.max(min, max);
        setValue(this.value, false);
    }

    /** Whether the drag's button is still held, so a release outside this element still ends the drag. */
    private boolean isMouseDown(int button) {
        var mui = getModularUI();
        return mui != null && mui.getLastMouseDownButton() == button;
    }

    /** The pointer's screen position, or this element's origin when it is not mounted yet. */
    private float pointerX() {
        var mui = getModularUI();
        return mui == null ? getContentX() : mui.getLastMouseX();
    }

    /** The drag's own geometry, so a mounted and an unmounted slider resolve it the same way. */
    private float trackWidth() {
        var width = getContentWidth();
        return width > 0 ? width : unmountedTrackWidth;
    }

    private void applyPointer(float mouseX) {
        var width = trackWidth();
        if (width <= 0) return;
        var origin = getContentX();
        var local = Mth.clamp(mouseX - origin, 0f, width);
        setValue(min + (local / width) * (max - min), true);
    }

    @Override
    public void drawBackgroundAdditional(@Nonnull GUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);
        var x = getContentX();
        var y = getContentY();
        var width = getContentWidth();
        var height = getContentHeight();
        if (width <= 0 || height <= 0) return;

        var fraction = max > min ? Mth.clamp((value - min) / (max - min), 0f, 1f) : 0f;
        var trackTop = y + (height - TRACK_HEIGHT) / 2f;
        var graphics = guiContext.graphics;
        // groove, the part of it behind the value, then the handle centred on the value
        com.lowdragmc.lowdraglib2.gui.util.DrawerHelper.drawSolidRect(graphics, x, trackTop, width, TRACK_HEIGHT,
                TRACK_COLOR);
        var fillWidth = fraction * width;
        if (fillWidth > 0) {
            com.lowdragmc.lowdraglib2.gui.util.DrawerHelper.drawSolidRect(graphics, x, trackTop, fillWidth,
                    TRACK_HEIGHT, FILL_COLOR);
        }
        var handleX = Mth.clamp(x + fillWidth - HANDLE_WIDTH / 2f, x, x + width - HANDLE_WIDTH);
        var handleInset = Math.min(2f, Math.max(0f, (height - 12f) / 2f));
        com.lowdragmc.lowdraglib2.gui.util.DrawerHelper.drawSolidRect(graphics, handleX, y + handleInset,
                HANDLE_WIDTH, height - handleInset * 2, dragging || hovered ? HANDLE_HOVER_COLOR : HANDLE_COLOR);
    }
}
