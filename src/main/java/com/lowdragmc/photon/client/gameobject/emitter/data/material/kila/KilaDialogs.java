package com.lowdragmc.photon.client.gameobject.emitter.data.material.kila;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.editor.resource.Resource;
import com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceProviderContainer;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** The material inspector's dialogs, built from the resource panel's cells and configurator groups. */
@OnlyIn(Dist.CLIENT)
public final class KilaDialogs {
    private static final int PRESET = 56;

    private KilaDialogs() {
    }

    static ScrollerView list() {
        var list = new ScrollerView();
        list.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
        list.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
        });
        return list;
    }

    /** A titled section of {@code list}, as a configurator group; returns the grid its cells go in. */
    static UIElement section(ScrollerView list, String title) {
        return section(list, Component.translatable(title));
    }

    static UIElement section(ScrollerView list, Component title) {
        var grid = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.wrap(FlexWrap.WRAP);
        });
        var group = new ConfiguratorGroup(title.getString(), false);
        group.addConfigurator(new Configurator().addInlineChild(grid));
        list.addScrollViewChild(group);
        return grid;
    }

    /** The resource panel's cell, showing {@code thumbnail} and wearing its selection look while {@code selected}. */
    static UIElement cell(UIElement thumbnail, Component name, int width, BooleanSupplier selected) {
        return cell(thumbnail, name, width, selected, true);
    }

    private static UIElement staticCell(UIElement thumbnail, Component name, int width) {
        return cell(thumbnail, name, width, () -> false, false);
    }

    private static UIElement cell(UIElement thumbnail, Component name, int width, BooleanSupplier selected,
                                 boolean refreshSelection) {
        var cell = ResourceProviderContainer.createResourceCell(Resource.DisplayMode.GRID, width, thumbnail, name.getString());
        var wash = ResourceProviderContainer.defaultSelectedTexture();
        Runnable sync = () -> {
            boolean on = selected.getAsBoolean();
            if (on == cell.hasClass("__selected__")) return;
            if (on) {
                cell.addClass("__selected__");
            } else {
                cell.removeClass("__selected__");
            }
            cell.style(style -> Style.defaultPipeline(style, s -> s.overlayTexture(on ? wash : IGuiTexture.EMPTY)));
        };
        sync.run();
        if (refreshSelection) cell.addEventListener(UIEvents.TICK, event -> sync.run());
        return cell;
    }

    static UIElement fill(IGuiTexture texture) {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
        }).style(style -> style.backgroundTexture(texture));
    }

    /** Presets by category, with lazy live previews; a click applies one. */
    public static Dialog presets(UIElement origin, float x, float y, Consumer<KilaMaterial> apply) {
        var dialog = new Dialog().windowMode(x, y, 280, 360);
        dialog.setTitle("kila.preset.gallery");
        // A live Kila thumbnail compiles its material's shader layout. Building all 89 tiles at once
        // makes opening this small gallery expensive, so keep only the selected category alive.
        var categoryBar = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightAuto();
            layout.flexDirection(FlexDirection.ROW);
            layout.wrap(FlexWrap.WRAP);
            layout.gapAll(2);
        });
        dialog.addContent(categoryBar);
        var targetBar = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightAuto();
            layout.flexDirection(FlexDirection.ROW);
            layout.wrap(FlexWrap.WRAP);
            layout.gapAll(2);
        });
        dialog.addContent(targetBar);
        var list = list();
        list.addClass("__kila-presets__");
        dialog.addContent(list);
        KilaPresets.Category[] selectedCategory = {KilaPresets.Category.BASIC};
        KilaPresets.Target[] selectedTarget = {null};
        java.util.function.Consumer<KilaPresets.Category> showCategory = category -> {
            selectedCategory[0] = category;
            list.clearAllScrollViewChildren();
            var grid = section(list, category.langKey());
            int shown = 0;
            for (var preset : KilaPresets.ALL) {
                if (preset.category() != category
                        || (selectedTarget[0] != null && preset.target() != selectedTarget[0])) continue;
                KilaMaterial[] cachedMaterial = {null};
                java.util.function.Supplier<KilaMaterial> material = () -> {
                    if (cachedMaterial[0] == null) cachedMaterial[0] = preset.create();
                    return cachedMaterial[0];
                };
                var name = Component.translatable(preset.langKey());
                var tips = new ArrayList<Component>(List.of(name));
                if (preset.target() != KilaPresets.Target.PARTICLE) {
                    tips.add(Component.translatable(preset.target().langKey()));
                }
                tips.add(Component.translatable(preset.langKey() + ".desc"));
                tips.add(Component.translatable("kila.preset.click_to_apply"));
                boolean[] hovered = {false};
                long[] hoverStarted = {0L};
                var preview = com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture.of(() ->
                        hovered[0] && System.nanoTime() - hoverStarted[0] >= 250_000_000L
                                ? material.get().preview() : com.lowdragmc.lowdraglib2.gui.texture.Icons.MATERIAL);
                var previewWidget = fill(preview);
                var cell = staticCell(fill(KilaSlotPreview.checker()).addChild(previewWidget), name, PRESET);
                previewWidget.addEventListener(UIEvents.MOUSE_ENTER, event -> {
                    hovered[0] = true;
                    hoverStarted[0] = System.nanoTime();
                });
                previewWidget.addEventListener(UIEvents.MOUSE_LEAVE, event -> hovered[0] = false);
                cell.addEventListener(UIEvents.MOUSE_ENTER, event -> {
                    hovered[0] = true;
                    hoverStarted[0] = System.nanoTime();
                });
                cell.addEventListener(UIEvents.MOUSE_LEAVE, event -> hovered[0] = false);
                cell.style(style -> style.tooltips(tips.toArray(Component[]::new)));
                cell.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                    if (event.button != 0) return;
                    // This instance already owns the live tile preview; reuse it instead of rebuilding every module.
                    apply.accept(material.get());
                    dialog.close();
                });
                grid.addChild(cell);
                shown++;
            }
            if (shown == 0) grid.addChild(new Label().setText("kila.preset.none"));
        };
        for (var category : KilaPresets.Category.values()) {
            categoryBar.addChild(new Button().setText(category.langKey())
                    .setOnClick(event -> showCategory.accept(category)));
        }
        targetBar.addChild(new Button().setText("kila.preset.target.all").setOnClick(event -> {
            selectedTarget[0] = null;
            showCategory.accept(selectedCategory[0]);
        }));
        for (var target : KilaPresets.Target.values()) {
            targetBar.addChild(new Button().setText(target.langKey()).setOnClick(event -> {
                selectedTarget[0] = target;
                showCategory.accept(selectedCategory[0]);
            }));
        }
        showCategory.accept(KilaPresets.Category.BASIC);
        dialog.addButton(new Button().setText("ldlib.gui.tips.cancel").setOnClick(e -> dialog.close())
                .addClass("__cancel-button__"));
        return dialog.show(origin.getModularUI());
    }

    /** The modules by category, each a row with what it does under it; the ones already on cannot be added. */
    public static Dialog modules(UIElement origin, float x, float y, KilaMaterial material, Consumer<KilaModule> add) {
        var dialog = new Dialog().windowMode(x, y, 220, 260);
        dialog.setTitle("kila.module.add.title");
        var list = list();
        list.addClass("__kila-modules__");
        for (var category : KilaModule.Category.values()) {
            var group = new ConfiguratorGroup("kila.module.category." + category.name().toLowerCase(java.util.Locale.ROOT), false);
            for (var module : material.modules()) {
                if (module.category() == category) group.addConfigurator(moduleEntry(dialog, module, add));
            }
            list.addScrollViewChild(group);
        }
        dialog.addContent(list);
        dialog.addButton(new Button().setText("ldlib.gui.tips.cancel").setOnClick(e -> dialog.close())
                .addClass("__cancel-button__"));
        return dialog.show(origin.getModularUI());
    }

    private static Configurator moduleEntry(Dialog dialog, KilaModule module, Consumer<KilaModule> add) {
        var entry = new Configurator(module.langKey());
        entry.addClass("__kila-modules_entry__");
        var button = new Button().setText(module.isEnable() ? "kila.module.add.added" : "kila.module.add.add");
        button.setActive(!module.isEnable());
        button.setOnClick(e -> {
            add.accept(module);
            dialog.close();
        });
        entry.addInlineChild(button);
        var description = new Label().setText(module.langKey() + ".desc");
        description.textStyle(style -> style.textWrap(TextWrap.WRAP).adaptiveHeight(true));
        description.layout(layout -> layout.widthPercent(100));
        description.addClass("__kila-modules_description__");
        entry.addChild(description);
        return entry;
    }
}

