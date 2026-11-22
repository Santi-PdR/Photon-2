package com.lowdragmc.photon.core.mixins.accessor;

import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The decoded-buffer cache, so a sound file replaced by a pack update is decoded again; see {@code FxPackSounds}. */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
    @Accessor("soundBuffers")
    SoundBufferLibrary photon$getSoundBuffers();
}
