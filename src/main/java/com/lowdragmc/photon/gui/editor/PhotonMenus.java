package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.util.ITreeNode;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import net.minecraft.network.chat.Component;

/**
 * LDLib2 2.2.46.a {@code TreeBuilder.Menu#disabledLeaf}, implemented against 2.2.39.a.
 * The menu mixin dims an entry whose content is {@link #DISABLED} and leaves the menu open on click.
 */
public final class PhotonMenus {
    private PhotonMenus() {
    }

    /**
     * What a {@link #disabledLeaf} runs, which is nothing — and how the menu mixin tells that it cannot be
     * picked.
     */
    public static final Runnable DISABLED = () -> {
    };

    public static TreeBuilder.Menu disabledLeaf(TreeBuilder.Menu menu, String name) {
        return menu.leaf(name, DISABLED);
    }

    public static TreeBuilder.Menu disabledLeaf(TreeBuilder.Menu menu, Component name) {
        return menu.leaf(name, DISABLED);
    }

    public static TreeBuilder.Menu disabledLeaf(TreeBuilder.Menu menu, IGuiTexture icon, Component name) {
        return menu.leaf(icon, name, DISABLED);
    }

    public static boolean isDisabled(ITreeNode<?, ?> node) {
        return node != null && node.getContent() == DISABLED;
    }
}
