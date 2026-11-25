package com.lowdragmc.photon.client.fx.timeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * The tickable sound instance an {@link AudioClip} plays while it is the active clip (looping), and the one-shot an
 * {@link AudioTrack#onRemove() audio track plays when the effect is removed}. Its lifecycle is driven by
 * {@link TimelinePlayer#applyAudio}: created (and queued) on clip enter, {@link #update}d each tick to reflect live
 * volume/pitch edits and a moving target, and {@link #requestStop() stopped} on clip exit/switch. When positional
 * (3D) it follows a supplied world position and fades with the listener's distance over the clip's
 * {@link AudioClip#range() range}; otherwise it plays relative to the listener at full volume.
 * <p>
 * The engine's own distance model is left off either way ({@link SoundInstance.Attenuation#NONE}): its LINEAR mode
 * fades over the sound file's {@code attenuation_distance} — 16 blocks unless the {@code sounds.json} says otherwise,
 * and an imported sound's never does — fixed per sound, not per clip, so an effect-sized sound was silent past 16
 * blocks and nothing in the editor could change that. The falloff is applied to the volume here instead, per tick,
 * over the clip's own range, and it follows the inverse-distance law rather than a linear ramp (see
 * {@link #falloff(double, float)}). A 3D source still plays from its position (not relative), so a mono sound pans —
 * OpenAL plays a stereo file everywhere, unpositioned, which is why {@link AudioConverter} imports sounds as mono.
 */
@OnlyIn(Dist.CLIENT)
public class TimelineSoundInstance extends AbstractTickableSoundInstance {
    private final TimelineSoundPosition position;
    /** The clip's own volume (its envelope); {@link #volume} is this after the distance falloff. */
    private float baseVolume;
    /** Blocks at which a positional sound has faded to silence; see {@link #falloff(double, float)}. */
    private float range;
    private boolean stopRequested = false;

    public TimelineSoundInstance(SoundEvent soundEvent, SoundSource source, float volume, float pitch,
                                 boolean looping, boolean attenuated, @Nullable Supplier<Vector3f> positionSupplier,
                                 float range) {
        super(soundEvent, source, RandomSource.create());
        this.looping = looping;
        this.delay = 0;
        this.pitch = pitch;
        this.baseVolume = volume;
        this.range = range;
        this.position = new TimelineSoundPosition(attenuated, positionSupplier);
        this.attenuation = SoundInstance.Attenuation.NONE;
        // non-positional: play at the listener
        this.relative = !this.position.isPositional();
        applyPosition(this.position.getPosition());
        this.volume = baseVolume * falloff();
    }

    /** Push live volume/pitch (and, when positional, an updated position source and range) onto the instance. */
    public void update(float volume, float pitch, @Nullable Supplier<Vector3f> positionSupplier, float range) {
        this.baseVolume = volume;
        this.pitch = pitch;
        this.range = range;
        this.position.update(positionSupplier);
        this.relative = !this.position.isPositional();
        applyPosition(this.position.getPosition());
        this.volume = baseVolume * falloff();
    }

    /** Whether this plays from a world position (3D) rather than at the listener. */
    public boolean isPositional() {
        return position.isPositional();
    }

    /**
     * Our volume is an envelope driven from the outside, so starting at zero is normal — without this
     * the engine refuses to start the sound at all ({@code SoundEngine.play}: "volume was zero"), and
     * since the instance is only created on clip enter it would never get a second chance, so a clip
     * whose volume curve begins at 0 would be silent for its whole span. Only the initial start is
     * gated; the per-tick loop happily rides a volume of 0 and back up again. A 3D sound whose listener
     * is out of range when it starts is the same case: silent now, heard when they come closer.
     */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** Ask the instance to stop on its next {@link #tick}; the sound engine then drops it. */
    public void requestStop() {
        this.stopRequested = true;
    }

    @Override
    public void tick() {
        if (stopRequested) {
            stop();
            return;
        }
        applyPosition(position.getPosition());
        this.volume = baseVolume * falloff();
    }

    /**
     * Decibels a positional sound has dropped by at the edge of its range, before the last stretch eases it into
     * silence: 30 dB is an eighth of the loudness, "faint". Under the inverse-distance law of {@link #falloff} it also
     * fixes the reference distance the sound is at full volume within — {@code range / 10^(30/20)}, a 32nd of the
     * range — and so the distance at which the sound is half as loud (10 dB down): a tenth of the range, whatever
     * the range is.
     */
    public static final float EDGE_DROP_DB = 30f;
    /** The fraction of the range over which the gain is eased from its edge value to zero (no step at the edge). */
    private static final double EASE_FRACTION = 0.1;

    /**
     * The gain a positional sound has for a listener {@code distance} blocks away: full within the reference distance
     * (a 32nd of the range: 8 blocks of 250), then the inverse-distance law — {@code reference / distance}, the way a
     * real point source carries and what OpenAL's default model does: half the gain, 6 dB, for every doubling of the
     * distance — down to {@link #EDGE_DROP_DB} at the edge, with the last tenth of the range easing into silence (no
     * step at the edge). Over 250 blocks: 0.79 at 10, 0.26 at 30, 0.13 at 60, 0.06 at 125, 0 at 250. The curves
     * before it were flat near the source, which is where a listener expects the change: the engine's linear gain
     * ({@code 1 - d/range}) was 6 dB down only at half the range ("it only updates every 20 blocks", as a tester put
     * it), and an even number of decibels per block was 4 dB down 30 blocks from a 250-block source — carrying across
     * an arena, as asked, but 30 blocks away it played as if at the listener's ear ("I'm 30 blocks from the fx and it
     * is right in my ear"). 1 for no range and for a distance that is not a number, so a broken position is heard
     * rather than lost.
     */
    public static float falloff(double distance, float range) {
        if (!(range > 0) || !(distance >= 0)) {
            return 1f;
        }
        var t = distance / range;
        if (t >= 1) {
            return 0f;
        }
        var reference = range * Math.pow(10, -EDGE_DROP_DB / 20);
        var gain = distance <= reference ? 1 : reference / distance;
        var ease = Math.min(1, (1 - t) / EASE_FRACTION);
        return (float) Mth.clamp(gain * ease, 0, 1);
    }

    private float falloff() {
        if (!position.isPositional()) {
            return 1f;
        }
        var listener = listenerPosition();
        if (listener == null) {
            return 1f;
        }
        var dx = x - listener.x;
        var dy = y - listener.y;
        var dz = z - listener.z;
        return falloff(Math.sqrt(dx * dx + dy * dy + dz * dz), range);
    }

    /** Where the sound engine's listener is: the camera, which is where {@code SoundManager.updateSource} puts it. */
    @Nullable
    private static Vec3 listenerPosition() {
        var minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameRenderer == null) {
            return null;
        }
        return minecraft.gameRenderer.getMainCamera().getPosition();
    }

    private void applyPosition(@Nullable Vector3f pos) {
        if (pos != null && pos.isFinite()) {
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
        }
    }
}
