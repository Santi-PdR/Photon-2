package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.trail.TrailEmitter;
import com.lowdragmc.photon.client.gameobject.particle.renderer.TileParticleRenderer;
import com.lowdragmc.photon.client.gameobject.particle.renderer.ParticleRotationMath;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Locks the public runtime API names and overloads shared with Photon 26.2. */
class PublicApiParityTest {
    @Test
    void tileParticleRendererPreservesReferenceInstanceStrides() {
        assertEquals(21, TileParticleRenderer.INSTANCE_FLOATS);
        assertEquals(15, TileParticleRenderer.MODEL_INSTANCE_FLOATS);
    }

    @Test
    void tileParticleRendererPreservesReferenceEulerRotationEntryPoint() {
        var rotation = new org.joml.Vector3f(0.3f, -0.7f, 1.1f);
        assertEquals(ParticleRotationMath.eulerRotation(rotation), TileParticleRenderer.eulerRotation(rotation));
    }

    @Test
    void materialPreservesTheReferenceInspectorApi() {
        assertApi(IMaterial.class, Set.of(
                "buildConfigurator/1", "copy/0", "createPreview/1", "preview/0", "previewLive/0",
                "serializeWrapper/0", "deserializeWrapper/1"));
    }

    @Test
    void fxObjectInterfacePreservesTheReferenceApi() {
        assertApi(IFXObject.class, Set.of(
                "copy/1", "copyTransformFrom/1", "deepCopy/0", "deserializeWrapper/1",
                "drawEditorAfterWorld/3", "emit/1", "emit/4", "emmit/1", "emmit/4",
                "getConfigurableName/0", "getDeltaTime/0", "getEffectExecutor/0", "getFXObjectType/0",
                "getIcon/0", "getLevel/0", "getName/0", "getSelfTimeScale/0", "inspectSceneInformation/2",
                "isActive/0", "isAlive/0", "isPlaying/0", "isSelfActive/0", "isSelfTimelineVisible/0",
                "isSelfVisible/0", "isVisible/0", "name/0", "remove/1", "reset/0", "serializeWrapper/0",
                "setDelay/1", "setEffect/1", "setLevel/1", "setName/1", "setSelfActive/1",
                "setSelfTimeScale/1", "setSelfTimelineVisible/1", "setSelfVisible/1", "shallowCopy/0",
                "timeScale/0", "updatePos/1", "updateRotation/1", "updateScale/1"));
    }

    @Test
    void particleEmitterInterfacePreservesTheReferenceApi() {
        assertApi(IParticleEmitter.class, Set.of(
                "getAge/0", "getCullBox/1", "getLightColor/1", "getLightColor/2", "getMemRandom/1",
                "getMemRandom/2", "getParticleAmount/0", "getRGBAColor/0", "getRandomSource/0",
                "getSimSpaceRotation/0", "getSimSpaceScale/0", "getSimToWorld/0", "getT/0", "getT/1",
                "getVelocity/0", "getWorldToSim/0", "inspectSceneInformation/2", "isLooping/0", "self/0",
                "setAge/1", "setRGBAColor/1"));
    }

    @Test
    void particleEmitterPreservesItsReferenceApiOutsideRenderPipelineHooks() {
        assertApi(ParticleEmitter.class, Set.of(
                "buildConfigurator/1", "clearsLightCacheOnTickBegin/0", "createNewParticle/0",
                "currentSpawnFrame/0", "drawEditorAfterWorld/3", "effectiveRenderPass/0", "emitParticle/1",
                "getAccumulatedDistance/0", "getActiveForceFields/0", "getCullBox/1", "getEmissionRateAccum/0",
                "getFXObjectType/0", "getIcon/0", "getLifetime/0", "getParticleAmount/0", "getParticleBatchCount/0",
                "getParticles/0", "getSimSpaceRotation/0", "getSimSpaceScale/0", "getSimToWorld/0",
                "getWorldToSim/0", "isLooping/0", "nextParticleBatchIndex/0", "onTickBegin/0", "remove/1",
                "rendererRuntime/0", "reset/0", "resolveSimSpaceTransform/0", "runtime/0",
                "scheduleSubEmitterSpawn/1", "setAccumulatedDistance/1", "setEmissionRateAccum/1",
                "shallowCopy/0", "update/1", "updateOrigin/0"));
    }

    @Test
    void beamEmitterPreservesItsReferenceApiOutsideRenderPipelineHooks() {
        assertApi(BeamEmitter.class, Set.of(
                "buildConfigurator/1", "effectiveRenderPass/0", "getConfig/0", "getCullBox/1",
                "getFXObjectType/0", "getIcon/0", "getLifetime/0", "getParticleAmount/0", "getStartDelay/0",
                "getT/0", "getT/1", "isLooping/0", "onTickBegin/0", "remove/1", "rendererRuntime/0",
                "reset/0", "runtime/0", "shallowCopy/0", "update/1", "updateOrigin/0"));
    }

    @Test
    void trailEmitterPreservesItsReferenceApiOutsideRenderPipelineHooks() {
        assertApi(TrailEmitter.class, Set.of(
                "buildConfigurator/1", "effectiveRenderPass/0", "getCullBox/1", "getFXObjectType/0",
                "getIcon/0", "getLifetime/0", "getParticleAmount/0", "getStartDelay/0", "isLooping/0",
                "onTickBegin/0", "remove/1", "rendererRuntime/0", "reset/0", "runtime/0", "shallowCopy/0",
                "update/1", "updateOrigin/0"));
    }

    @Test
    void effectResourcePreservesTheReferenceApi() {
        assertApi(FX.class, Set.of("createInternalRuntime/0", "createRuntime/0", "createRuntime/1",
                "deserializeNBT/2", "getFxData/0", "getFxLocation/0", "serializeNBT/1", "setFxLocation/1"));
    }

    @Test
    void effectExecutorPreservesTheReferenceApi() {
        assertApi(FXEffectExecutor.class, Set.of("getFx/0", "getLevel/0", "getRuntime/0", "notifyFinished/0",
                "resetFinishedNotification/0", "retire/2", "runtimeEnded/0", "setAllowMulti/1", "setDelay/1",
                "setForcedDeath/1", "setOffset/1", "setOnFinished/1", "setRotation/1", "setScale/1",
                "shouldSkipStart/1"));
    }

    @Test
    void configurableEffectExecutorInterfacePreservesTheReferenceApi() {
        assertApi(IEffectExecutor.class, Set.of("getLevel/0", "getRandomSource/0", "onTimelineSignal/4",
                "postEffectSink/0", "updateFXObjectFrame/2", "updateFXObjectTick/1"));
    }

    @Test
    void particleTickHostPreservesTheReferenceApi() {
        assertApi(ParticleTickHost.class, Set.of("generation/0", "tickCount/0"));
    }

    @Test
    void runtimePreservesTheReferenceApi() {
        assertApi(FXRuntime.class, Set.of(
                "addSceneObjectInternal/1", "destroy/1", "emit/1", "emit/2", "emmit/1", "emmit/2",
                "findObject/1", "findObjects/1", "getAllSceneObjects/0", "getFxData/0", "getGenerationAtEmit/0",
                "getHost/0", "getObjects/0", "getRate/0", "getRoot/0", "getSceneObject/1",
                "getTimelinePlayer/0", "isAlive/0", "isDestroyed/0", "isEmitted/0", "isFinished/0",
                "isValid/0", "removeSceneObjectInternal/1", "setRate/1"));
    }

    @Test
    void fxDataPreservesTheReferenceApi() {
        assertApi(FXData.class, Set.of("copy/1", "deserializeNBT/2", "objects/0", "serializeNBT/1", "timeline/0"));
    }

    @Test
    void effectExecutorInterfacePreservesTheReferenceApi() {
        assertApi(IFXEffectExecutor.class, Set.of("getFx/0", "setAllowMulti/1", "setDelay/1", "setForcedDeath/1",
                "setOffset/1", "setOffset/3", "setRotation/1", "setRotation/3", "setScale/1", "setScale/3",
                "start/0"));
    }

    @Test
    void blockEffectExecutorPreservesTheReferenceApi() {
        assertApi(BlockEffectExecutor.class, Set.of("setCheckState/1", "start/0", "updateFXObjectTick/1"));
    }

    @Test
    void entityEffectExecutorPreservesTheReferenceApi() {
        assertApi(EntityEffectExecutor.class, Set.of("start/0", "updateFXObjectFrame/2", "updateFXObjectTick/1"));
    }

    private static void assertApi(Class<?> type, Set<String> expected) {
        var actual = publicAndProtectedMethods(type);
        assertTrue(actual.containsAll(expected), () -> type.getSimpleName()
                + " is missing Photon 26.2 API members: " + difference(expected, actual));
    }

    private static Set<String> publicAndProtectedMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())
                        || Modifier.isProtected(method.getModifiers()))
                .map(PublicApiParityTest::shape)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        return expected.stream().filter(member -> !actual.contains(member)).collect(Collectors.toSet());
    }

    private static String shape(Method method) {
        return method.getName() + "/" + method.getParameterCount();
    }
}
