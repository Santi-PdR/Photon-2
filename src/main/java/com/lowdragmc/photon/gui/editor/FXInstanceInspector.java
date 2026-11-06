package com.lowdragmc.photon.gui.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.client.fx.instance.InstanceTags;
import com.lowdragmc.photon.client.gameobject.FXInstanceObject;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.network.chat.Component;

import java.util.function.UnaryOperator;

/** Inspector affordances for an FX instance and the objects placed by it. */
public final class FXInstanceInspector {
    private FXInstanceInspector() {
    }

    public static void decorate(FXEditor editor, ConfiguratorGroup root, IFXObject object) {
        switch (editor.instances.role(object)) {
            case INSTANCE, MISSING -> root.addConfiguratorAt(instanceHeader(editor, (FXInstanceObject) object), 0);
            case PLACED -> root.addConfiguratorAt(placedHeader(editor, object), 0);
            default -> {
            }
        }
    }

    private static Configurator instanceHeader(FXEditor editor, FXInstanceObject instance) {
        var placement = instance.getPlacement();
        var placed = placement != null && placement.isPlaced();
        var fx = instance.getFx() == null ? "?" : instance.getFx().toString();
        var header = new Configurator() {
            @Override
            protected TreeBuilder.Menu createMenu() {
                var menu = super.createMenu();
                instanceActions(editor, instance, menu, UnaryOperator.identity());
                return menu;
            }
        };
        header.setId("photon_fx_instance_header");
        header.setLabel(Component.translatable(placed ? "photon.fx_instance.header" : "photon.fx_instance.header.missing", fx));
        if (!placed) {
            header.label.textStyle(style -> style.textColor(FXInstanceStyle.MISSING_COLOR));
        }
        return header;
    }

    private static Configurator placedHeader(FXEditor editor, IFXObject object) {
        var instances = editor.instances;
        var owner = instances.owner(object);
        var header = new Configurator() {
            @Override
            protected TreeBuilder.Menu createMenu() {
                var menu = super.createMenu();
                if (instances.isOverridden(object)) {
                    menu.crossLine();
                    menu.leaf(Icons.REPLAY, "photon.fx_instance.revert_object", () -> instances.revertObject(object));
                }
                return menu;
            }
        };
        header.setId("photon_fx_instance_placed_header");
        header.setLabel(Component.translatable("photon.fx_instance.placed_by", owner == null ? "?" : owner.getName(),
                owner == null || owner.getFx() == null ? "?" : owner.getFx().toString()));
        header.label.textStyle(style -> style.textColor(FXInstanceStyle.PLACED_COLOR));
        return header;
    }

    /** Actions shared by the instance header and the hierarchy context menu. */
    public static void instanceActions(FXEditor editor, FXInstanceObject instance, TreeBuilder.Menu menu,
                                       UnaryOperator<Runnable> afterwards) {
        var instances = editor.instances;
        var placed = instances.role(instance) == FXInstanceEditing.Role.INSTANCE;
        if (instance.getFx() != null) {
            menu.leaf("photon.fx_instance.open_source", () -> FXFileActions.openSource(editor, instance.getFx()));
        } else {
            menu.leaf("photon.fx_instance.open_source", () -> {
            });
        }
        if (placed) {
            menu.leaf("photon.fx_instance.overrides", () -> FXInstanceOverridesDialog.show(editor, instance));
            menu.leaf("photon.fx_instance.revert_all", afterwards.apply(() -> instances.revertAll(instance)));
        } else {
            menu.leaf("photon.fx_instance.overrides", () -> {
            });
            menu.leaf("photon.fx_instance.revert_all", () -> {
            });
        }
        menu.leaf("photon.fx_instance.replace",
                () -> FXInstanceStyle.pickFx(editor, fx -> instances.replace(instance, fx)));
    }
}