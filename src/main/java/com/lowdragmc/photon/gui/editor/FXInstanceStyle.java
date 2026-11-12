package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.photon.client.fx.FXHelper;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.function.Consumer;

/** How FX instances look in the editor, and the picker for the effect an instance places. */
public final class FXInstanceStyle {
    public static final int PLACED_COLOR = 0xFF8CB4FF;
    public static final int MISSING_COLOR = 0xFFFF6B6B;

    private FXInstanceStyle() {
    }

    /** Ask for an effect by id, from every effect the game can load (mod jars, resource packs, fx packs). */
    public static void pickFx(FXEditor editor, Consumer<ResourceLocation> onPicked) {
        var chosen = new String[]{""};
        var dialog = new Dialog();
        dialog.overlay.layout(layout -> layout.width(280).maxWidthPercent(95).height(280).maxHeightPercent(80));
        dialog.setTitle("photon.fx_instance.pick");
        var content = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(4);
            layout.flex(1);
        });

        var list = new ScrollerView();
        list.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
        list.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
            layout.height(160);
        });

        var search = new TextField();
        search.setId("photon_fx_pick_search");
        search.layout(layout -> layout.widthPercent(100).height(16));
        search.setTextResponder(word -> fillFxList(list, word, chosen));

        content.addChild(new Label().setText("photon.fx_instance.pick.search"));
        content.addChild(search);
        content.addChild(list);

        // kept so the instance-editor scenario can still set the choice through onUpdate; the
        // visible list above is what the author uses
        var configurator = new SearchComponentConfigurator<>("photon.fx_instance.pick.fx",
                () -> chosen[0], value -> {
                    chosen[0] = value == null ? "" : value;
                    fillFxList(list, search.getValue(), chosen);
                }, "", true,
                (word, handler) -> FXHelper.searchFX(word, handler, false),
                value -> value,
                UIElementProvider.text(value -> Component.literal(value == null || value.isEmpty() ? "---" : value)));
        configurator.layout(layout -> layout.widthPercent(100).height(0));
        content.addChild(configurator);

        fillFxList(list, "", chosen);
        dialog.addContent(content);
        dialog.addButton(new Button().setOnClick(e -> {
            var id = chosen[0].isEmpty() ? null : ResourceLocation.tryParse(chosen[0]);
            dialog.close();
            if (id == null) return;
            try {
                onPicked.accept(id);
            } catch (RuntimeException error) {
                com.lowdragmc.photon.Photon.LOGGER.error("Failed to place FX instance {}", id, error);
                FXFileActions.notify(editor, "photon.fx_instance",
                        Component.translatable("photon.fx_instance.unreadable", id.toString()));
            }
        }).setText("ldlib.gui.tips.confirm").addClass("__confirm-button__"));
        dialog.addButton(new Button().setText("ldlib.gui.tips.cancel").setOnClick(e -> dialog.close()));
        dialog.show(editor);
    }

    /** The always-visible catalogue, filtered by {@code word} (empty word = everything). */
    static void fillFxList(ScrollerView list, String word, String[] chosen) {
        list.clearAllScrollViewChildren();
        var search = word == null ? "" : word.toLowerCase(Locale.ROOT);
        for (var fx : FXHelper.listAllFX()) {
            var id = fx.toString();
            if (!search.isEmpty() && !id.toLowerCase(Locale.ROOT).contains(search)) {
                continue;
            }
            var row = new Button().setText(id).setOnClick(e -> chosen[0] = id);
            row.layout(layout -> layout.widthPercent(100).height(14));
            list.addScrollViewChild(row);
        }
    }
}
