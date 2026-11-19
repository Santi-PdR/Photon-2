package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Menu;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.ITreeNode;
import com.lowdragmc.photon.gui.editor.PhotonMenus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Function;

/**
 * Back-port of LDLib2 2.2.46.a disabled menu entries onto the bundled 2.2.39.a jar:
 * dimmed, not lit under the mouse, and a click does nothing and leaves the menu open.
 */
@Mixin(value = Menu.class, remap = false)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MenuMixin {
    @Shadow
    public abstract Map getNodeUIs();

    @Shadow
    private Function textureProvider;

    @Inject(method = "initMenu", at = @At("TAIL"), remap = false)
    @SuppressWarnings("unchecked")
    private void photon$dimDisabledLeaves(CallbackInfo ci) {
        var textures = (Function<ITreeNode<?, ?>, IGuiTexture>) textureProvider;
        for (var entry : ((Map<?, UIElement>) getNodeUIs()).entrySet()) {
            if (!(entry.getKey() instanceof ITreeNode<?, ?> node) || !PhotonMenus.isDisabled(node)) {
                continue;
            }
            var container = entry.getValue();
            container.addClass("__menu_disabled-node__");
            photon$greyText(container);
            container.addEventListener(UIEvents.MOUSE_DOWN, e -> {
                if (e.button == 0) {
                    e.stopImmediatePropagation();
                }
            }, true);
            container.addEventListener(UIEvents.MOUSE_ENTER, e ->
                    e.currentElement.style(style -> style.backgroundTexture(textures.apply(node))));
        }
    }

    /** Dims an entry through its text colour — drawn at an opacity, the entry came out blank. */
    @Unique
    private static void photon$greyText(UIElement element) {
        if (element instanceof TextElement text) {
            text.textStyle(style -> style.textColor(ColorPattern.GRAY.color));
        }
        for (var child : element.getChildren()) {
            photon$greyText(child);
        }
    }
}
