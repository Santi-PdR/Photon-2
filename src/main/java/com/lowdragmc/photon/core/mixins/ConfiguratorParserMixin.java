package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.configurator.ConfiguratorParser;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.HeaderConfigurator;
import com.lowdragmc.photon.gui.editor.PhotonConfigurators;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * LDLib2 2.2.46.a tags each inspector row with the field it edits ({@code setSourceField}).
 * 2.2.39.a never does, so FX instance override marks have nothing to hang on. Stamp the field
 * as each row is added.
 */
@Mixin(value = ConfiguratorParser.class, remap = false)
public class ConfiguratorParserMixin {
    private static final ThreadLocal<Field> PHOTON$FIELD = new ThreadLocal<>();

    @Inject(method = "createFieldConfigurator", at = @At("HEAD"), remap = false)
    private static void photon$begin(Field field, ConfiguratorGroup father, Class<?> clazz,
                                     Map<?, ?> setters, Object object, CallbackInfo ci) {
        PHOTON$FIELD.set(field);
    }

    @Inject(method = "createFieldConfigurator", at = @At("RETURN"), remap = false)
    private static void photon$end(Field field, ConfiguratorGroup father, Class<?> clazz,
                                   Map<?, ?> setters, Object object, CallbackInfo ci) {
        PHOTON$FIELD.remove();
    }

    @ModifyArg(method = "createFieldConfigurator",
            at = @At(value = "INVOKE",
                    target = "Lcom/lowdragmc/lowdraglib2/configurator/ui/ConfiguratorGroup;addConfigurators([Lcom/lowdragmc/lowdraglib2/configurator/ui/Configurator;)Lcom/lowdragmc/lowdraglib2/configurator/ui/ConfiguratorGroup;"),
            remap = false, require = 0)
    private static Configurator[] photon$tagMany(Configurator[] configs) {
        photon$tag(configs);
        return configs;
    }

    @ModifyArg(method = "createFieldConfigurator",
            at = @At(value = "INVOKE",
                    target = "Lcom/lowdragmc/lowdraglib2/configurator/ui/ConfiguratorGroup;addConfigurator(Lcom/lowdragmc/lowdraglib2/configurator/ui/Configurator;)Lcom/lowdragmc/lowdraglib2/configurator/ui/ConfiguratorGroup;"),
            remap = false, require = 0)
    private static Configurator photon$tagOne(Configurator config) {
        photon$tag(config);
        return config;
    }

    private static void photon$tag(Configurator... configs) {
        Field field = PHOTON$FIELD.get();
        if (field == null || configs == null) return;
        for (var config : configs) {
            if (config == null || config instanceof HeaderConfigurator) continue;
            PhotonConfigurators.setSourceField(config, field);
        }
    }
}
