package com.lowdragmc.photon.client.gameobject.emitter.particle;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.LazyManaged;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.photon.client.gameobject.emitter.aratrail.AraPhysicsSetting;
import com.lowdragmc.photon.client.gameobject.emitter.aratrail.AraTrailConfig;
import com.lowdragmc.photon.client.gameobject.emitter.aratrail.TrailSection;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamConfig;
import com.lowdragmc.photon.client.gameobject.emitter.trail.TrailConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.forcefield.ForceFieldConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleRendererSettingPersistenceTest {
    @Test
    void renderModeIsPersistedSoModelProjectsDoNotRestoreAsBillboards() throws NoSuchFieldException {
        Field renderMode = ParticleRendererSetting.class.getDeclaredField("renderMode");

        assertTrue(renderMode.isAnnotationPresent(Persisted.class));
        assertEquals(ParticleRendererSetting.Mode.Billboard, new ParticleRendererSetting(null).getRenderMode());
    }

    @Test
    void particleConfigRecursivelyPersistsItsRendererSettings() throws NoSuchFieldException {
        Field renderer = ParticleConfig.class.getDeclaredField("renderer");

        assertTrue(renderer.isAnnotationPresent(Persisted.class));
        assertTrue(renderer.getAnnotation(Persisted.class).subPersisted());
    }

    @Test
    void particleConfigPersistsItsVisualEmitterInputs() throws NoSuchFieldException {
        assertPersisted("startSize");
        assertPersisted("startLifetime");
        assertPersisted("emission", true);
        assertPersisted("shape", true);
        assertPersisted("sizeOverLifetime", true);
    }

    @Test
    void gpuInstancingChoiceSurvivesEmitterProjectSerialization() throws NoSuchFieldException {
        Field useGPUInstance = ParticleRendererSetting.class.getDeclaredField("useGPUInstance");

        assertTrue(useGPUInstance.isAnnotationPresent(Persisted.class));
    }

    @Test
    void emitterDataConfiguratorsAreVisibleToTheLegacyProjectSerializer() throws Exception {
        URI classesLocation = ParticleConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path dataClasses = Path.of(classesLocation).resolve("com/lowdragmc/photon/client/gameobject/emitter/data");

        try (Stream<Path> classes = Files.walk(dataClasses)) {
            for (Path classFile : classes.filter(path -> path.toString().endsWith(".class")).toList()) {
                String relativeName = dataClasses.relativize(classFile).toString()
                        .replace(classFile.getFileSystem().getSeparator(), ".");
                String className = "com.lowdragmc.photon.client.gameobject.emitter.data."
                        + relativeName.substring(0, relativeName.length() - ".class".length());
                Class<?> dataClass = Class.forName(className, false, ParticleConfig.class.getClassLoader());

                for (Field field : dataClass.getDeclaredFields()) {
                    Configurable configurable = field.getAnnotation(Configurable.class);
                    if (configurable == null) continue;

                    Persisted persisted = field.getAnnotation(Persisted.class);
                    if (persisted == null && field.getName().equals("hdr")
                            && IMaterial.class.isAssignableFrom(dataClass)) {
                        continue; // Serialized explicitly through HDRColor.CODEC by the material bridge.
                    }
                    assertTrue(persisted != null,
                            () -> className + "." + field.getName() + " must be persisted by LDLib 1");
                    assertLazyManagedFunctionField(className, field);
                    if (configurable.subConfigurable()) {
                        assertTrue(persisted.subPersisted(),
                                () -> className + "." + field.getName() + " must recursively persist its sub-config");
                    }
                }
            }
        }
    }

    @Test
    void nonParticleEmitterConfigsAreVisibleToTheLegacyProjectSerializer() {
        for (Class<?> configClass : new Class<?>[]{
                AraTrailConfig.class,
                AraPhysicsSetting.class,
                TrailSection.class,
                BeamConfig.class,
                TrailConfig.class,
                ForceFieldConfig.class
        }) {
            for (Field field : configClass.getDeclaredFields()) {
                Configurable configurable = field.getAnnotation(Configurable.class);
                if (configurable == null) continue;

                Persisted persisted = field.getAnnotation(Persisted.class);
                assertTrue(persisted != null,
                        () -> configClass.getName() + "." + field.getName() + " must be persisted by LDLib 1");
                assertLazyManagedFunctionField(configClass.getName(), field);
                if (configurable.subConfigurable()) {
                    assertTrue(persisted.subPersisted(),
                            () -> configClass.getName() + "." + field.getName() + " must recursively persist its sub-config");
                }
            }
        }
    }

    private static void assertPersisted(String fieldName) throws NoSuchFieldException {
        assertTrue(ParticleConfig.class.getDeclaredField(fieldName).isAnnotationPresent(Persisted.class), fieldName);
    }

    private static void assertPersisted(String fieldName, boolean subPersisted) throws NoSuchFieldException {
        Field field = ParticleConfig.class.getDeclaredField(fieldName);

        assertTrue(field.isAnnotationPresent(Persisted.class), fieldName);
        assertEquals(subPersisted, field.getAnnotation(Persisted.class).subPersisted(), fieldName);
    }

    private static void assertLazyManagedFunctionField(String owner, Field field) {
        if (field.getType() == NumberFunction.class || field.getType() == NumberFunction3.class) {
            assertTrue(field.isAnnotationPresent(LazyManaged.class),
                    () -> owner + "." + field.getName() + " must be lazy for LDLib 1 persistence");
        }
    }

}
