package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.gui.editor.PhotonConfiguratorExt;
import com.lowdragmc.photon.gui.editor.PhotonConfigurators;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Back-port of LDLib2 2.2.46.a {@code Configurator} inspector APIs onto the bundled 2.2.39.a jar:
 * source field, overridden mark, and menu contributors.
 */
@Mixin(value = Configurator.class, remap = false)
public abstract class ConfiguratorMixin implements PhotonConfiguratorExt {
    private static final int OVERRIDDEN_COLOR = 0xFF4A9EFF;

    @Shadow @Final public UIElement lineContainer;
    @Shadow @Final public com.lowdragmc.lowdraglib2.gui.ui.elements.Label label;

    @Shadow
    protected abstract TreeBuilder.Menu createMenu();

    @Unique @Nullable
    private Field photon$sourceField;
    @Unique
    private boolean photon$overridden;
    @Unique @Nullable
    private Component photon$plainLabel;
    @Unique @Nullable
    private UIElement photon$overriddenMark;
    @Unique
    private final List<BiConsumer<Configurator, TreeBuilder.Menu>> photon$menuContributors = new ArrayList<>();

    @Override
    public Field photon$getSourceField() {
        return photon$sourceField;
    }

    @Override
    public void photon$setSourceField(Field field) {
        this.photon$sourceField = field;
    }

    @Override
    public boolean photon$isOverridden() {
        return photon$overridden;
    }

    @Override
    public void photon$setOverridden(boolean overridden) {
        if (this.photon$overridden == overridden) return;
        this.photon$overridden = overridden;
        if (overridden) {
            this.photon$plainLabel = null;
            photon$reapplyOverridden();
        } else if (photon$plainLabel != null) {
            this.label.setText(photon$plainLabel);
            this.photon$plainLabel = null;
        }
        if (photon$overriddenMark == null) {
            photon$overriddenMark = new UIElement().addClass("__configurator_overridden__").layout(layout -> {
                layout.width(2);
                layout.height(14);
            }).style(style -> style.backgroundTexture(new ColorRectTexture(OVERRIDDEN_COLOR)));
            this.lineContainer.addChildAt(photon$overriddenMark, 0);
        }
        photon$overriddenMark.setDisplay(overridden);
    }

    @Override
    public void photon$addMenuContributor(BiConsumer<Configurator, TreeBuilder.Menu> contributor) {
        photon$menuContributors.add(contributor);
    }

    @Override
    public List<BiConsumer<Configurator, TreeBuilder.Menu>> photon$menuContributors() {
        return photon$menuContributors;
    }

    @Unique
    private void photon$reapplyOverridden() {
        if (photon$overridden) {
            this.photon$plainLabel = this.label.getText();
            this.label.setText(photon$plainLabel.copy().withStyle(ChatFormatting.BOLD));
        }
    }

    @Inject(method = "setLabel(Lnet/minecraft/network/chat/Component;)Lcom/lowdragmc/lowdraglib2/configurator/ui/Configurator;",
            at = @At("RETURN"), remap = false)
    private void photon$reapplyAfterLabel(Component name, CallbackInfoReturnable<Configurator> cir) {
        photon$reapplyOverridden();
    }

    @Redirect(method = "onMouseDown",
            at = @At(value = "INVOKE",
                    target = "Lcom/lowdragmc/lowdraglib2/configurator/ui/Configurator;createMenu()Lcom/lowdragmc/lowdraglib2/gui/util/TreeBuilder$Menu;"),
            remap = false)
    private TreeBuilder.Menu photon$contributeMenu(Configurator self) {
        var menu = this.createMenu();
        PhotonConfigurators.contribute(self, menu);
        return menu;
    }
}
