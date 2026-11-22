package com.lowdragmc.photon.core.mixins.accessor;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Decoded buffers by sound file; vanilla only ever empties it whole, on a reload. See {@code FxPackSounds}. */
@Mixin(SoundBufferLibrary.class)
public interface SoundBufferLibraryAccessor {
    @Accessor("cache")
    Map<ResourceLocation, CompletableFuture<SoundBuffer>> photon$getCache();
}
