package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the public runtime API names and overloads shared with Photon 26.2. */
class PublicApiParityTest {
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
    void effectExecutorInterfacePreservesTheReferenceApi() {
        assertApi(IEffectExecutor.class, Set.of("getLevel/0", "getRandomSource/0", "onTimelineSignal/4",
                "postEffectSink/0", "updateFXObjectFrame/2", "updateFXObjectTick/1"));
    }

    @Test
    void particleTickHostPreservesTheReferenceApi() {
        assertApi(ParticleTickHost.class, Set.of("generation/0", "tickCount/0"));
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
