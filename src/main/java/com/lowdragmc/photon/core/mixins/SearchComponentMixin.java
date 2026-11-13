package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.gui.ui.elements.SearchComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opening a search box must offer the <b>whole</b> catalogue, not the one value already chosen.
 * <p>
 * The box in LDLib 2.2.39 runs its search when it opens using whatever text the field already holds, and that text
 * is the current value's name — written there by {@code hide()} every time a selection is made. So opening a picker
 * shows a dropdown with exactly the value already picked (or nothing, on a box that was never used), and the only
 * way to see anything else is to type a character. Typing appends to that leftover name instead of replacing it,
 * so the query becomes "chosenNameX" and matches nothing: the list looks dead.
 * <p>
 * This is LDLib's own fix, made upstream in 2.2.39.8 ("a search box offering only the value already chosen until a
 * character was typed"), back-ported here because the version bundled with this port cannot be replaced.
 * <p>
 * It fixes every picker built on this widget at once: the effect id in a sub-emitter ("particles"), the effect id of
 * an FX instance ("fx list"), the material and preset selectors that use a search box, blocks and entities in
 * collision settings, etc.
 */
@Mixin(value = SearchComponent.class, remap = false)
public abstract class SearchComponentMixin {
    @Shadow(remap = false)
    private com.lowdragmc.lowdraglib2.gui.ui.elements.TextField textField;

    @Shadow(remap = false)
    private void onSearchWordChanged(String word) {
    }

    @Shadow(remap = false)
    public abstract Object getValue();

    /**
     * Runs after the box opened. The original search already ran with the leftover name; re-running it with the
     * empty query supersedes it (the search engine drops the results of any query that is no longer the current
     * one, so the two do not interleave), and selecting the text is what makes the first keystroke replace the old
     * name instead of being appended to it.
     * <p>
     * {@code show()} can run again while the dropdown is already open (layout, refocus). If the field no longer
     * holds the chosen value, the author is filtering — do not wipe that query back to the whole catalogue.
     */
    @Inject(method = "show", at = @At("TAIL"), remap = false)
    private void photon$showWholeCatalogue(CallbackInfo ci) {
        if (textField == null) {
            return;
        }
        var text = textField.getValue();
        var selected = getValue();
        var selectedText = selected == null ? "" : String.valueOf(selected);
        boolean leftover = text == null || text.isEmpty() || text.equals(selectedText);
        if (!leftover) {
            return;
        }
        textField.setSelection(0, text == null ? 0 : text.length());
        onSearchWordChanged("");
    }
}
