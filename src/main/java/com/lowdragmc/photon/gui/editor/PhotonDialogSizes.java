package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.Photon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

import java.io.File;

/**
 * LDLib2 2.2.46.a {@code DialogSizeStore}: the sizes window-mode dialogs were last resized to,
 * kept across sessions next to the editor layouts. 2.2.39.a has no such store.
 */
public final class PhotonDialogSizes {
    /** The smallest a window-mode dialog can be resized to in LDLib2 2.2.46.a. */
    static final float MIN_WINDOW_SIZE = 50;

    @Nullable
    private static CompoundTag cached;

    private PhotonDialogSizes() {
    }

    /**
     * The size {@code key} was last closed at, or null when none is stored — or when the entry is smaller than a
     * resize can make a window ({@link #MIN_WINDOW_SIZE}), which no real closing writes: such an entry is damage
     * (the 0×0 that {@link #remember} used to store, see there), and the caller's default size is the right answer,
     * not a window clamped to the resize minimum that fits one word per line.
     */
    @Nullable
    public static Vector2f get(String key) {
        var root = read();
        if (!root.contains(key, Tag.TAG_COMPOUND)) return null;
        var size = root.getCompound(key);
        float width = size.getFloat("width");
        float height = size.getFloat("height");
        if (!(width >= MIN_WINDOW_SIZE) || !(height >= MIN_WINDOW_SIZE)) return null;
        return new Vector2f(width, height);
    }

    public static void put(String key, float width, float height) {
        var root = read();
        var size = new CompoundTag();
        size.putFloat("width", width);
        size.putFloat("height", height);
        if (size.equals(root.get(key))) return;
        root.put(key, size);
        try {
            var file = file();
            var parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            NbtIo.write(root, file);
        } catch (Exception e) {
            Photon.LOGGER.warn("Failed to save the size of dialog {}", key, e);
        }
    }

    /**
     * Remember the overlay size when the dialog closes. Call after {@code windowMode}.
     * <p>
     * The size is sampled while the window is still on screen — at every layout of the overlay and as it is taken
     * out of the UI — and the last sample is what the close callback stores. It cannot be read in the callback
     * itself: {@code Dialog.close()} detaches the dialog from the UI first and runs the callback after, and a
     * detached element has no layout, so its overlay measures 0×0. Reading it there stored 0×0, {@link #get}
     * then opened the dialog at the resize minimum, and the second use of "Export All Projects to FXPack..." was a
     * 50-pixel window with the explanation wrapped one word per line over the buttons. A dialog closed before it
     * was ever laid out stores nothing.
     */
    public static void remember(Dialog dialog, String key) {
        var overlay = dialog.overlay;
        var last = new Vector2f(Float.NaN, Float.NaN);
        UIEventListener sample = event -> {
            float width = overlay.getSizeWidth();
            float height = overlay.getSizeHeight();
            if (width >= MIN_WINDOW_SIZE && height >= MIN_WINDOW_SIZE) {
                last.set(width, height);
            }
        };
        overlay.addEventListener(UIEvents.LAYOUT_CHANGED, sample);
        // the removal event fires inside removeChild, before the element loses its UI: the final size, resizes included
        overlay.addEventListener(UIEvents.REMOVED, sample);
        dialog.setOnClose(() -> {
            if (Float.isFinite(last.x) && Float.isFinite(last.y)) {
                put(key, last.x, last.y);
            }
        });
    }

    private static File file() {
        return new File(new File(LDLib2.getAssetsDir().getParentFile(), "editor_layouts"), "dialog_sizes.nbt");
    }

    private static CompoundTag read() {
        if (cached == null) {
            cached = new CompoundTag();
            var file = file();
            if (file.exists()) {
                try {
                    var tag = NbtIo.read(file);
                    if (tag != null) cached = tag;
                } catch (Exception ignored) {
                    // a corrupt file costs the remembered sizes, not the dialog
                }
            }
        }
        return cached;
    }
}
