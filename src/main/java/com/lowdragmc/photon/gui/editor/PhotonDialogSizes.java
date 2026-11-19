package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
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

    @Nullable
    public static Vector2f get(String key) {
        var root = read();
        if (!root.contains(key, Tag.TAG_COMPOUND)) return null;
        var size = root.getCompound(key);
        // no smaller than a resize can make it: a damaged entry must not open the dialog at nothing
        return new Vector2f(Math.max(MIN_WINDOW_SIZE, size.getFloat("width")),
                Math.max(MIN_WINDOW_SIZE, size.getFloat("height")));
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
     */
    public static void remember(Dialog dialog, String key) {
        dialog.setOnClose(() -> put(key, dialog.overlay.getSizeWidth(), dialog.overlay.getSizeHeight()));
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
