package com.lowdragmc.photon.client.fx.timeline;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.command.EffectRemoval;
import com.lowdragmc.photon.gui.editor.view.timeline.PhotonTrackTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Pins down how an effect sounds from afar and on its way out, three behaviours that were reported broken from a
 * server (a 3D clip that was heard at the same volume everywhere; no way to give an aura an ending sound; a remove
 * that cut the effect in one frame instead of letting it fade):
 * <ol>
 * <li>A 3D clip plays from the effect's root when its track has no target (it used to need one, silently), with a
 * linear falloff over its own range, and the sound engine's distance model is kept out of it — its range is the
 * sound file's {@code attenuation_distance}, 16 blocks for every imported sound, which no clip setting could change.</li>
 * <li>An audio track's "on remove" sound is saved with the track, copied with it, and started exactly once by
 * {@link FXRuntime#destroy}, forced or not — and not by {@link FXRuntime#discard()}, which is a swap.</li>
 * <li>{@code /photon fx remove} without a {@code force} argument is the graceful removal.</li>
 * </ol>
 * Runs inside the client smoke test, where a level, a player and the sound registry exist.
 */
@OnlyIn(Dist.CLIENT)
public final class AudioRemovalRegression {
    private AudioRemovalRegression() {
    }

    public static void verify(Minecraft minecraft) {
        verifyFalloff();
        verifyRootFallback();
        verifyPersistence();
        verifyRemovalSound(minecraft);
        check(!EffectRemoval.force(false, false) && !EffectRemoval.force(true, false) && EffectRemoval.force(true, true),
                "/photon fx remove without a force argument is not the graceful removal");
    }

    private static void verifyFalloff() {
        check(TimelineSoundInstance.falloff(0, 48) == 1f, "a listener at the source does not hear full volume");
        check(Math.abs(TimelineSoundInstance.falloff(30, 48) - 0.375f) < 1e-4f,
                "30 blocks out of 48 is not heard at three eighths: " + TimelineSoundInstance.falloff(30, 48));
        check(TimelineSoundInstance.falloff(48, 48) == 0f && TimelineSoundInstance.falloff(100, 48) == 0f,
                "a listener at or past the range still hears the sound");
        check(TimelineSoundInstance.falloff(Double.NaN, 48) == 1f && TimelineSoundInstance.falloff(10, 0) == 1f
                && TimelineSoundInstance.falloff(10, Float.NaN) == 1f, "a broken distance or range did not fall back to audible");
        var event = SoundEvent.createVariableRangeEvent(Photon.id("audio_removal_regression"));
        var positional = new TimelineSoundInstance(event, SoundSource.MASTER, 1f, 1f, false, true,
                () -> new Vector3f(1, 2, 3), 48f);
        check(positional.isPositional() && !positional.isRelative()
                        && positional.getAttenuation() == SoundInstance.Attenuation.NONE
                        && positional.getX() == 1 && positional.getY() == 2 && positional.getZ() == 3,
                "a 3D sound instance is not a non-relative source at its position with the engine's own attenuation off");
        var flat = new TimelineSoundInstance(event, SoundSource.MASTER, 1f, 1f, true, false, null, 48f);
        check(!flat.isPositional() && flat.isRelative(), "a non-3D sound instance does not play at the listener");
        // the flag without a position source (the editor preview) is non-positional too
        var preview = new TimelineSoundInstance(event, SoundSource.MASTER, 1f, 1f, true, true, null, 48f);
        check(!preview.isPositional() && preview.isRelative(), "a 3D flag with no position source was treated as positional");
        check(positional.canStartSilent(), "a 3D sound whose listener starts out of range (volume 0) would be refused by the engine");
    }

    private static void verifyRootFallback() {
        var runtime = new FXRuntime(new FXData());
        runtime.root.updatePos(new Vector3f(10, 20, 30));
        var fromRoot = TimelinePlayer.soundPosition(runtime, null).get();
        var fromMissing = TimelinePlayer.soundPosition(runtime, UUID.randomUUID()).get();
        var fromRootId = TimelinePlayer.soundPosition(runtime, FXRuntime.ROOT_UUID).get();
        check(fromRoot != null && fromRoot.distance(10, 20, 30) < 1e-3f, "a track with no target does not play from the effect's root: " + fromRoot);
        check(fromMissing != null && fromMissing.distance(10, 20, 30) < 1e-3f, "a track whose target is gone does not play from the root: " + fromMissing);
        check(fromRootId != null && fromRootId.distance(10, 20, 30) < 1e-3f, "a track bound to the root does not play from it: " + fromRootId);
    }

    private static void verifyPersistence() {
        var provider = Platform.getFrozenRegistry();
        var track = (AudioTrack) PhotonTrackTypes.AUDIO.create();
        var clip = new AudioClip(0, 40, 1).attenuation(true).range(72);
        track.clips().add(clip);
        track.onRemoveEnabled(true);
        track.onRemove().sound(new ResourceLocation("minecraft", "block.anvil.land")).range(96)
                .volume(NumberFunction.constant(0.5f)).category(SoundSource.PLAYERS).loop(true);
        var back = (AudioTrack) PhotonTrackTypes.AUDIO.create();
        back.readData(provider, track.writeData(provider));
        var backClip = back.clips().get(0) instanceof AudioClip audio ? audio : null;
        check(backClip != null && backClip.attenuation() && backClip.range() == 72f, "a clip's 3D range did not survive saving");
        check(back.onRemoveEnabled() && back.onRemove().sound().equals(new ResourceLocation("minecraft", "block.anvil.land"))
                        && back.onRemove().range() == 96f && Math.abs(back.onRemove().volumeAt(0) - 0.5f) < 1e-4f
                        && back.onRemove().category() == SoundSource.PLAYERS && !back.onRemove().loop(),
                "the on-remove sound did not survive saving as a one-shot");
        var copy = (AudioTrack) track.copy();
        check(copy.onRemoveEnabled() && copy.onRemove() != track.onRemove() && copy.onRemove().range() == 96f
                && !copy.onRemove().loop(), "copying a track did not copy its on-remove sound");
        // an untouched track saves the same bytes as before the setting existed
        var plain = (AudioTrack) PhotonTrackTypes.AUDIO.create();
        check(!plain.writeData(provider).contains("onRemove"), "a track without an on-remove sound saves one");
        var disabled = (AudioTrack) PhotonTrackTypes.AUDIO.create();
        disabled.readData(provider, plain.writeData(provider));
        check(!disabled.onRemoveEnabled(), "a track saved without an on-remove sound reads one back");
        // a clip saved before the 3D flag or the range existed: not positional, and the default range
        var old = new CompoundTag();
        old.putString("sound", "minecraft:ui.button.click");
        var oldClip = new AudioClip(0, 10, 1);
        AudioTrack.readAudio(oldClip, old);
        check(!oldClip.attenuation() && oldClip.range() == AudioClip.DEFAULT_RANGE,
                "a clip from before the 3D settings existed did not read back as it was");
        check(new AudioClip(0, 10, 1).attenuation() && new AudioClip(0, 10, 1).range(Float.NaN).range() == AudioClip.DEFAULT_RANGE
                && new AudioClip(0, 10, 1).range(10_000).range() == AudioClip.MAX_RANGE, "a new clip is not 3D with a bounded range");
    }

    private static void verifyRemovalSound(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            throw new IllegalStateException("The removal-sound check needs a level and a player");
        }
        check(minecraft.getSoundManager().getSoundEvent(new ResourceLocation("minecraft", "block.anvil.land")) != null,
                "the sound registry has no anvil sound to play");
        // a graceful remove starts the sound once, and forcing the same removal afterwards does not start it again
        var executor = start(minecraft);
        var runtime = executor.getRuntime();
        try {
            check(runtime != null && runtime.getRemovalSoundsStarted() == 0, "a running effect already played its removal sound");
            runtime.destroy(false);
            check(runtime.getRemovalSoundsStarted() == 1, "a graceful remove did not start the on-remove sound once: " + runtime.getRemovalSoundsStarted());
            runtime.destroy(true);
            runtime.destroy(true);
            check(runtime.getRemovalSoundsStarted() == 1, "removing an effect again replayed its removal sound");
        } finally {
            retire(minecraft, executor);
        }
        // a forced remove (the entity died, /photon fx remove ... true) plays it too
        var forced = start(minecraft);
        try {
            forced.getRuntime().destroy(true);
            check(forced.getRuntime().getRemovalSoundsStarted() == 1, "a forced remove did not play the on-remove sound");
        } finally {
            retire(minecraft, forced);
        }
        // a swap (the effect file was reloaded, a newer effect supersedes it) is not a removal
        var swapped = start(minecraft);
        try {
            swapped.getRuntime().discard();
            check(swapped.getRuntime().getRemovalSoundsStarted() == 0, "discarding a swapped-out effect played its removal sound");
        } finally {
            retire(minecraft, swapped);
        }
        // a muted track plays nothing (the runtime plays a copy of the effect's data: mute that one)
        var silent = start(minecraft);
        try {
            for (var track : silent.getRuntime().fxData.timeline().tracks()) track.mute(true);
            silent.getRuntime().destroy(false);
            check(silent.getRuntime().getRemovalSoundsStarted() == 0, "a muted audio track played its removal sound");
        } finally {
            retire(minecraft, silent);
        }
    }

    /** An effect on the player whose single audio track plays the anvil on removal; started, so it has a runtime. */
    private static EntityEffectExecutor start(Minecraft minecraft) {
        var fx = new FX();
        var track = (AudioTrack) PhotonTrackTypes.AUDIO.create();
        track.onRemoveEnabled(true);
        track.onRemove().sound(new ResourceLocation("minecraft", "block.anvil.land"));
        fx.getFxData().timeline().tracks().add(track);
        var executor = new EntityEffectExecutor(fx, minecraft.level, minecraft.player, EntityEffectExecutor.AutoRotate.NONE);
        // location-less effects on one entity count as the same effect to the dedup; this is not about that
        executor.setAllowMulti(true);
        executor.start();
        if (executor.getRuntime() == null) {
            throw new IllegalStateException("The regression effect did not start");
        }
        return executor;
    }

    private static void retire(Minecraft minecraft, EntityEffectExecutor executor) {
        if (executor.getRuntime() != null) executor.getRuntime().discard();
        var effects = EntityEffectExecutor.CACHE.get(minecraft.player);
        if (effects != null) {
            effects.remove(executor);
            if (effects.isEmpty()) EntityEffectExecutor.CACHE.remove(minecraft.player);
        }
    }

    private static void check(boolean condition, String failure) {
        if (!condition) {
            throw new IllegalStateException(failure);
        }
    }
}
