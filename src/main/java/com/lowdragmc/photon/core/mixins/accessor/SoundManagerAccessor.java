package com.lowdragmc.photon.core.mixins.accessor;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * The sound registry and the {@code .ogg} snapshot a reload fills, which is the only place the sound engine reads
 * files from. {@code FxPackSounds} adds and removes the entries of fx packs mounted without a reload.
 */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
    @Accessor("registry")
    Map<ResourceLocation, WeighedSoundEvents> photon$getRegistry();

    @Accessor("soundCache")
    Map<ResourceLocation, Resource> photon$getSoundCache();

    @Accessor("soundEngine")
    SoundEngine photon$getSoundEngine();
}
