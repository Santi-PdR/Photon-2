package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import net.minecraft.nbt.EndTag;
import net.minecraft.resources.ResourceLocation;
import com.lowdragmc.lowdraglib2.registry.AutoRegistry;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AnimatedGltfAdditionalNbtTest {
    @BeforeAll
    static void initializeModelSourceCodecRegistry() {
        var registry = PhotonRegistries.MODEL_SOURCES;
        if (registry == null) {
            registry = AutoRegistry.LDLibRegisterClient.<IModelSource, Supplier<IModelSource>>create(
                    Photon.id("model_source"), IModelSource.class, AutoRegistry::noArgsCreator);
            PhotonRegistries.MODEL_SOURCES = registry;
        }
        var annotation = AnimatedGltfModelSource.class.getAnnotation(LDLRegisterClient.class);
        if (!registry.containKey(annotation.name())) {
            registry.register(annotation.name(), AutoRegistry.Holder
                    .<LDLRegisterClient, IModelSource, Supplier<IModelSource>>of(annotation,
                            AnimatedGltfModelSource.class, AnimatedGltfModelSource::new));
        }
    }

    @Test
    void persistsAnimationFilesThroughLdlibAdditionalNbtHooks() {
        var expected = List.of(ResourceLocation.fromNamespaceAndPath("example", "walk.glb"),
                ResourceLocation.fromNamespaceAndPath("other", "run.gltf"));
        var source = new AnimatedGltfModelSource();
        source.setAnimationFiles(expected);

        var additional = source.serializeAdditionalNBT(null);
        var restored = new AnimatedGltfModelSource();
        restored.deserializeAdditionalNBT(additional, null);

        assertEquals(expected, restored.getAnimationFiles());
    }

    @Test
    void omitsEmptyAdditionalPayloadAndClearsReusedSourceWhenAbsent() {
        var empty = new AnimatedGltfModelSource();
        assertSame(EndTag.INSTANCE, empty.serializeAdditionalNBT(null));

        var reused = new AnimatedGltfModelSource();
        reused.setAnimationFiles(List.of(ResourceLocation.fromNamespaceAndPath("example", "old.glb")));
        reused.deserializeAdditionalNBT(EndTag.INSTANCE, null);

        assertEquals(List.of(), reused.getAnimationFiles());
    }
}
