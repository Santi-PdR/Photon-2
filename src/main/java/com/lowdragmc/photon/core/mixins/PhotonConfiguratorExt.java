package com.lowdragmc.photon.core.mixins;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * LDLib2 2.2.46.a APIs that 2.2.39.a does not have, implemented on {@link Configurator} via mixin
 * so the FX instance inspector can mark and revert overridden rows.
 */
public interface PhotonConfiguratorExt {
    Field photon$getSourceField();

    void photon$setSourceField(Field field);

    boolean photon$isOverridden();

    void photon$setOverridden(boolean overridden);

    void photon$addMenuContributor(BiConsumer<Configurator, TreeBuilder.Menu> contributor);

    List<BiConsumer<Configurator, TreeBuilder.Menu>> photon$menuContributors();
}
