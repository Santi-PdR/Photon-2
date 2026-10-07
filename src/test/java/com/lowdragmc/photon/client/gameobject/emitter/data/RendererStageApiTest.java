package com.lowdragmc.photon.client.gameobject.emitter.data;

import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.FXCompositeMode;
import com.lowdragmc.photon.client.render.PhotonStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RendererStageApiTest {
    @Test
    void exposesThe26Point2RuntimeStageApi() throws NoSuchMethodException {
        assertNotNull(RendererSetting.Runtime.class.getMethod("effectiveStage"));
        assertEquals(PhotonStage.AFTER_OPAQUE_FEATURES, RendererSetting.Layer.Opaque.stage);
        assertEquals(PhotonStage.AFTER_TRANSLUCENT_PARTICLES, RendererSetting.Layer.Translucent.stage);
        assertEquals(PhotonStage.AFTER_TRANSLUCENT_PARTICLES, PhotonStage.LAST);
    }

    @Test
    void defersOnlySafeTranslucentWorldPasses() {
        assertEquals(PhotonStage.DEFERRED, RendererSetting.resolveEffectiveStage(
                RendererSetting.Layer.Translucent, FXCompositeMode.LATE, true, false));
        assertEquals(PhotonStage.AFTER_OPAQUE_FEATURES, RendererSetting.resolveEffectiveStage(
                RendererSetting.Layer.Opaque, FXCompositeMode.LATE, true, false));
        assertEquals(PhotonStage.AFTER_TRANSLUCENT_PARTICLES, RendererSetting.resolveEffectiveStage(
                RendererSetting.Layer.Translucent, FXCompositeMode.VANILLA, true, false));
        assertEquals(PhotonStage.AFTER_TRANSLUCENT_PARTICLES, RendererSetting.resolveEffectiveStage(
                RendererSetting.Layer.Translucent, FXCompositeMode.LATE, false, false));
        assertEquals(PhotonStage.AFTER_TRANSLUCENT_PARTICLES, RendererSetting.resolveEffectiveStage(
                RendererSetting.Layer.Translucent, FXCompositeMode.LATE, true, true));
    }
}
