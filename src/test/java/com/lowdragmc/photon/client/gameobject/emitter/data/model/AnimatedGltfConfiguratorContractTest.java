package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigList;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimatedGltfConfiguratorContractTest {
    @Test
    void externalAnimationListUsesTheExpectedLdlibListConfiguratorContract() throws Exception {
        var field = AnimatedGltfModelSource.class.getDeclaredField("animationFiles");
        var list = field.getAnnotation(ConfigList.class);
        var configurable = field.getAnnotation(Configurable.class);

        assertEquals(List.class, field.getType());
        assertEquals(ResourceLocation.class,
                ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]);
        assertTrue(list.canAdd());
        assertTrue(list.canRemove());
        assertTrue(list.canReorder());
        assertFalse(configurable.persisted(),
                "ResourceLocation lists are stored through additional NBT on Forge 1.20.1");

        var rowFactory = AnimatedGltfModelSource.class.getDeclaredMethod(
                list.configuratorMethod(), Supplier.class, Consumer.class);
        assertEquals(Configurator.class, rowFactory.getReturnType());
        assertEquals(ResourceLocation.class,
                AnimatedGltfModelSource.class.getDeclaredMethod(list.addDefaultMethod()).getReturnType());
    }
}
