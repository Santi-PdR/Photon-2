package com.lowdragmc.photon.core.mixins.accessor;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Exposes Forge's in-progress model-loader map so Photon can alias NeoForge OBJ assets on Forge. */
@Mixin(ModelEvent.RegisterGeometryLoaders.class)
public interface RegisterGeometryLoadersAccessor {
    @Accessor(value = "loaders", remap = false)
    Map<ResourceLocation, IGeometryLoader<?>> photon$getLoaders();
}
