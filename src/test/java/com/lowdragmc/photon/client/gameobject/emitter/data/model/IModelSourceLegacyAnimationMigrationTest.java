package com.lowdragmc.photon.client.gameobject.emitter.data.model;

import com.lowdragmc.lowdraglib2.registry.AutoRegistry;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class IModelSourceLegacyAnimationMigrationTest {
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
    void wrapperMovesTheLegacyStringIntoAdditionalNbtBeforeCodecDecoding() {
        var data = new CompoundTag();
        data.putString("animationFiles", "example:walk.glb, other:run.gltf");
        var serialized = new CompoundTag();
        serialized.putString("type", "animated_gltf_model");
        serialized.put("data", data);

        var decoded = assertInstanceOf(AnimatedGltfModelSource.class,
                IModelSource.deserializeWrapper(serialized), serialized.toString());

        assertEquals(List.of(ResourceLocation.fromNamespaceAndPath("example", "walk.glb"),
                        ResourceLocation.fromNamespaceAndPath("other", "run.gltf")),
                decoded.getAnimationFiles());
        assertEquals("example:walk.glb, other:run.gltf", data.getString("animationFiles"),
                "deserialization must migrate a copy rather than mutate the caller's tag");
    }
}
