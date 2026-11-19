package com.lowdragmc.photon.gui.editor;

import com.google.common.base.Strings;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.function.BiConsumer;

/**
 * LDLib2 2.2.46.a configurator helpers, implemented against 2.2.39.a via {@link PhotonConfiguratorExt}.
 */
public final class PhotonConfigurators {
    private PhotonConfigurators() {
    }

    public static void setSourceField(Configurator configurator, Field field) {
        if (configurator instanceof PhotonConfiguratorExt ext) ext.photon$setSourceField(field);
    }

    @Nullable
    public static Field getSourceField(Configurator configurator) {
        return configurator instanceof PhotonConfiguratorExt ext ? ext.photon$getSourceField() : null;
    }

    public static void setOverridden(Configurator configurator, boolean overridden) {
        if (configurator instanceof PhotonConfiguratorExt ext) ext.photon$setOverridden(overridden);
    }

    public static void addMenuContributor(Configurator configurator,
                                          BiConsumer<Configurator, TreeBuilder.Menu> contributor) {
        if (configurator instanceof PhotonConfiguratorExt ext) ext.photon$addMenuContributor(contributor);
    }

    public static void contribute(Configurator clicked, @Nullable TreeBuilder.Menu menu) {
        if (menu == null) return;
        for (UIElement element = clicked; element != null; element = element.getParent()) {
            if (element instanceof PhotonConfiguratorExt ext) {
                for (var contributor : ext.photon$menuContributors()) {
                    contributor.accept(clicked, menu);
                }
            }
        }
    }

    /**
     * The key {@code field} is saved under, or null when it is not saved — same rule as LDLib2 2.2.46.a
     * {@code PersistedParser.persistedKey}.
     */
    @Nullable
    public static String persistedKey(Field field) {
        if (field.isAnnotationPresent(Configurable.class)) {
            Configurable configurable = field.getAnnotation(Configurable.class);
            if (!configurable.persisted()) return null;
            return Strings.isNullOrEmpty(configurable.key()) ? field.getName() : configurable.key();
        }
        if (field.isAnnotationPresent(Persisted.class)) {
            Persisted persisted = field.getAnnotation(Persisted.class);
            return Strings.isNullOrEmpty(persisted.key()) ? field.getName() : persisted.key();
        }
        return null;
    }

    /**
     * Whether a saved sub-object field writes its values into its owner's rather than under its own key.
     */
    public static boolean isPersistedFlat(Field field) {
        if (field.isAnnotationPresent(Configurable.class)) {
            Configurable configurable = field.getAnnotation(Configurable.class);
            return configurable.subConfigurable() && configurable.subFlattenPersisted();
        }
        if (field.isAnnotationPresent(Persisted.class)) {
            Persisted persisted = field.getAnnotation(Persisted.class);
            return persisted.subPersisted() && persisted.subFlattenPersisted();
        }
        return false;
    }
}
