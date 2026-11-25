package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.photon.Photon;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * A window-mode dialog remembers the size it had on screen when it closes. {@code Dialog.close()} detaches the dialog
 * from the UI before it runs the close callback, and a detached overlay measures 0×0: {@link PhotonDialogSizes#remember}
 * used to read the overlay in that callback, so the first close of "Export All Projects to FXPack..." (and of the pack
 * contents window) stored 0×0, which {@link PhotonDialogSizes#get} clamped to the 50-pixel resize minimum — every later
 * opening was a window the size of a button, its explanation wrapped one word per line over the title and the buttons
 * ("the GUI is broken"). Two halves, because the size only exists once the UI has laid the dialog out: {@link #open}
 * shows one on the live editor, {@link #verify} a few ticks later closes it and compares what was stored with what was
 * on screen. Also pins the self-heal for what every installation that used the dialog before the fix has on disk: a
 * stored 0×0 (or a size that is not a number) reads as no entry, so the dialog opens at its default size again.
 * Runs inside the client smoke test.
 */
@OnlyIn(Dist.CLIENT)
public final class DialogSizeRegression {
    static final String KEY = "photon_smoke_dialog_size_live";
    private static final float WIDTH = 300;
    private static final float HEIGHT = 200;
    @Nullable
    private static Dialog dialog;

    private DialogSizeRegression() {
    }

    public static void open(Editor editor) {
        PhotonDialogSizes.put(KEY, 0, 0);
        check(PhotonDialogSizes.get(KEY) == null, "a stored 0x0 was not read as no entry: " + PhotonDialogSizes.get(KEY));
        PhotonDialogSizes.put(KEY, Float.NaN, Float.NaN);
        check(PhotonDialogSizes.get(KEY) == null, "a stored NaN size was not read as no entry: " + PhotonDialogSizes.get(KEY));
        var opened = new Dialog().windowMode(editor.getPositionX() + 40, editor.getPositionY() + 40, WIDTH, HEIGHT);
        PhotonDialogSizes.remember(opened, KEY);
        opened.setTitle("photon.export_fxpack_all");
        opened.addContent(new Label().setText("photon.export_fxpack_all.export"));
        opened.addButton(new Button().setOnClick(e -> opened.close()).setText("ldlib.gui.tips.cancel"));
        opened.show(editor);
        dialog = opened;
    }

    /**
     * Whether the UI has laid the dialog out yet: that happens on a rendered frame, not on a tick, and a slow software
     * renderer can run several ticks between frames (round 24: with no resource reload at login the frames around the
     * dialog are the first busy ones of the session, and six ticks were not always a frame).
     */
    public static boolean laidOut() {
        var opened = dialog;
        return opened != null && opened.overlay.getSizeWidth() > 100 && opened.overlay.getSizeHeight() > 100;
    }

    public static void verify() {
        var opened = dialog;
        dialog = null;
        check(opened != null, "the dialog was not opened first");
        float width = opened.overlay.getSizeWidth();
        float height = opened.overlay.getSizeHeight();
        check(width > 100 && height > 100, "the window-mode dialog has no layout on screen: " + width + "x" + height);
        opened.close();
        var stored = PhotonDialogSizes.get(KEY);
        check(stored != null && Math.abs(stored.x - width) < 0.5f && Math.abs(stored.y - height) < 0.5f,
                "closing the dialog did not store the size it had on screen (" + width + "x" + height + "): " + stored);
        Photon.LOGGER.info("[dialog-size-close-smoke] a window-mode dialog stores the size it had on screen when it closes, and a damaged 0x0 entry reads as no entry");
    }

    private static void check(boolean condition, String failure) {
        if (!condition) {
            throw new IllegalStateException(failure);
        }
    }
}
