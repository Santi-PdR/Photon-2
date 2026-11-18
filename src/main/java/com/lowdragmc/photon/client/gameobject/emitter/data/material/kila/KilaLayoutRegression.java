package com.lowdragmc.photon.client.gameobject.emitter.data.material.kila;

import com.lowdragmc.photon.client.gameobject.emitter.data.material.MaterialContext;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** Regression for transactional rollback when a module exceeds Kila's physical sampler budget. */
@OnlyIn(Dist.CLIENT)
public final class KilaLayoutRegression {
    private KilaLayoutRegression() {
    }

    private static KilaModule textures(String id, int count) {
        return new KilaModule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Category category() {
                return Category.TEXTURE;
            }

            @Override
            public void declare(KilaLayout.Builder layout) {
                for (int i = 0; i < count; i++) {
                    layout.texture(id + "_" + i,
                            new KilaTexture(new ResourceLocation("photon", "regression/" + id + "_" + i + ".png")));
                }
            }

            @Override
            public void apply(ShaderInstance shader, MaterialContext context, float[] scratch) {
            }

            @Override
            public void buildModuleConfigurator(com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup group) {
            }
        };
    }

    public static void verify() {
        var kept = textures("kept", KilaLayout.MAX_TEXTURES - 1);
        var overflow = textures("overflow", 2);
        var layout = KilaLayout.surface().build(List.of(kept, overflow));
        if (layout.textures.size() != KilaLayout.MAX_TEXTURES - 1
                || layout.samplers.size() != KilaLayout.MAX_TEXTURES
                || layout.dropped.size() != 1 || layout.dropped.get(0) != overflow
                || layout.key.contains("overflow")
                || layout.defines.contains("KILA_TEX9")) {
            throw new IllegalStateException("Kila sampler overflow did not roll back the module atomically");
        }
    }
}
