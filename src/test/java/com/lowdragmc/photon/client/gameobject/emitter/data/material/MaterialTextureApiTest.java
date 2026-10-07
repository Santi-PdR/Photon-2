package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MaterialTextureApiTest {
    @Test
    void exposesDynamicTextureAndResourceLocationSamplerApis() throws NoSuchMethodException {
        assertEquals(DynamicTexture.class, CurveTexture.class.getMethod("getCurveTexture").getReturnType());
        assertEquals(ResourceLocation.class, CurveTexture.class.getMethod("textureId").getReturnType());
        assertEquals(DynamicTexture.class, GradientTexture.class.getMethod("getGradientTexture").getReturnType());
        assertEquals(ResourceLocation.class, GradientTexture.class.getMethod("textureId").getReturnType());
        assertNotNull(CurveTexture.class.getMethod("close"));
        assertNotNull(GradientTexture.class.getMethod("close"));
    }
}
