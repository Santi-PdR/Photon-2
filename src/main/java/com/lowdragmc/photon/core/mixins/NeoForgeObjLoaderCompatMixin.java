package com.lowdragmc.photon.core.mixins;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.ExtendedBlockModelDeserializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Maps model JSON produced for NeoForge to Forge's equivalent OBJ loader. */
@Mixin(value = ExtendedBlockModelDeserializer.class, remap = false)
public abstract class NeoForgeObjLoaderCompatMixin {
    @ModifyArg(
            method = "deserializeGeometry",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/model/geometry/GeometryLoaderManager;get(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraftforge/client/model/geometry/IGeometryLoader;",
                    remap = false
            ),
            index = 0,
            remap = false
    )
    private static ResourceLocation photon$useForgeObjLoader(ResourceLocation loaderId) {
        if (loaderId.getNamespace().equals("neoforge") && loaderId.getPath().equals("obj")) {
            return new ResourceLocation("forge", "obj");
        }
        return loaderId;
    }
}
