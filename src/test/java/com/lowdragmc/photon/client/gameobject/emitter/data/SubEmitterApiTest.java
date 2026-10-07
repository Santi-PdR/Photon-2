package com.lowdragmc.photon.client.gameobject.emitter.data;

import com.lowdragmc.photon.client.gameobject.emitter.data.SubEmittersSetting.Emitter;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SubEmitterApiTest {
    @Test
    void nestedEmitterExposesItsConfigurationForJavaIntegrations() throws NoSuchMethodException {
        assertNotNull(Emitter.class.getMethod("getFxLocation"));
        assertNotNull(Emitter.class.getMethod("setFxLocation", ResourceLocation.class));
        assertNotNull(Emitter.class.getMethod("getEvent"));
        assertNotNull(Emitter.class.getMethod("setEvent", SubEmittersSetting.Event.class));
        assertNotNull(Emitter.class.getMethod("isInheritColor"));
        assertNotNull(Emitter.class.getMethod("setInheritColor", boolean.class));
        assertNotNull(Emitter.class.getMethod("getEmitProbability"));
        assertNotNull(Emitter.class.getMethod("setEmitProbability",
                com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction.class));
    }
}
