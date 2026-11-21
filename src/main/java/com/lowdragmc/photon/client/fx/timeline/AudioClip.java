package com.lowdragmc.photon.client.fx.timeline;

import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A {@link Clip} on an {@link AudioTrack}: plays a registered {@code SoundEvent} for the clip's span.
 * The sound is stored by its {@link ResourceLocation} (registry id) so it serializes cleanly, plus
 * volume / pitch / category / 3D attenuation + range. It is intentionally a client-side subclass (unlike
 * the {@code net.minecraft}-free base {@link Clip}) so it can reference the sound registry types directly.
 * <p>
 * An {@link AudioTrack}'s "on remove" sound is an {@code AudioClip} too (same fields, never on the
 * timeline): its envelopes are sampled at their start since it plays once, as a one-shot.
 * <p>
 * The sound is <b>always looping</b>; {@link #duration()} is the clip's span on the timeline and so
 * decides when the timeline stops it (a clip shorter than the sound cuts it early, a longer one loops
 * into the next round). The repeat sub-divisions the editor draws inside the clip come from the
 * sound's own length, not from the clip.
 * <p>
 * Volume and pitch are {@link NumberFunction}s sampled at the normalized clip progress, like
 * {@link PostProcessClip}'s weight — a curve over the clip IS the fade / sweep envelope. The sound
 * engine re-reads both from a tickable instance every tick, so they take effect live.
 */
@OnlyIn(Dist.CLIENT)
public class AudioClip extends Clip {
    /** Default sound of a freshly created clip (a short, always-present vanilla sound). */
    public static final ResourceLocation DEFAULT_SOUND = SoundEvents.UI_BUTTON_CLICK.value().getLocation();
    /**
     * Default {@link #range()}: an effect worth a timeline is usually big (an aura, a boss ability) and wanted
     * audible from across a whole arena, lower the further away one stands — 250 blocks is what its authors asked
     * for after trying 48 ("make it 250 by default, heard at that distance, quieter and from over there when far").
     * The falloff is perceptual ({@link TimelineSoundInstance#falloff}), so even over 250 blocks a listener hears
     * the sound grow and fade with every step rather than only in the last stretch.
     */
    public static final float DEFAULT_RANGE = 250f;
    /** Bounds for {@link #range(float)}; a range this small is as good as off, this large as good as global. */
    public static final float MIN_RANGE = 1f, MAX_RANGE = 512f;

    /** The SoundEvent registry id to play; never null (defaults to {@link #DEFAULT_SOUND}). */
    private ResourceLocation sound = DEFAULT_SOUND;
    /** Volume over the clip, sampled at normalized clip progress. */
    private NumberFunction volume = NumberFunction.constant(1);
    /** Pitch over the clip, sampled like {@link #volume}. */
    private NumberFunction pitch = NumberFunction.constant(1);
    private SoundSource category = SoundSource.MASTER;
    /** Repeat the sound until the clip ends. Defaults to true for compatibility with existing projects. */
    private boolean loop = true;
    /**
     * 3D sound: played from the track's target object (the effect's root when none is bound) and fading with
     * the listener's distance over {@link #range()}. Off: the same volume for everyone, wherever they stand.
     * A new clip is 3D; projects saved before the setting existed read back as what they were (off).
     */
    private boolean attenuation = true;
    /** Blocks from the sound's position at which a 3D sound fades to silence; see {@link #range()}. */
    private float range = DEFAULT_RANGE;

    public AudioClip() {
        super();
    }

    public AudioClip(double start, double duration, float speed) {
        super(start, duration, speed);
    }

    public ResourceLocation sound() {
        return sound;
    }

    public AudioClip sound(ResourceLocation sound) {
        this.sound = sound;
        return this;
    }

    public NumberFunction volume() {
        return volume;
    }

    public AudioClip volume(NumberFunction volume) {
        this.volume = volume == null ? NumberFunction.constant(1) : volume;
        return this;
    }

    public NumberFunction pitch() {
        return pitch;
    }

    public AudioClip pitch(NumberFunction pitch) {
        this.pitch = pitch == null ? NumberFunction.constant(1) : pitch;
        return this;
    }

    /** Volume at clip-local time; never negative (the sound engine treats <0 as garbage). */
    public float volumeAt(double localTime) {
        return Math.max(0f, volume.get(progress(localTime), this::lerpValue).floatValue());
    }

    /** Pitch at clip-local time, clamped to the range the sound engine accepts. */
    public float pitchAt(double localTime) {
        return net.minecraft.util.Mth.clamp(pitch.get(progress(localTime), this::lerpValue).floatValue(), 0.5f, 2f);
    }

    public SoundSource category() {
        return category;
    }

    public AudioClip category(SoundSource category) {
        this.category = category;
        return this;
    }

    public boolean loop() {
        return loop;
    }

    public AudioClip loop(boolean loop) {
        this.loop = loop;
        return this;
    }

    public boolean attenuation() {
        return attenuation;
    }

    public AudioClip attenuation(boolean attenuation) {
        this.attenuation = attenuation;
        return this;
    }

    /**
     * How far a 3D sound carries, in blocks: full volume at the source, silent at this distance, and in between
     * the loudness falls evenly with distance — half as loud a third of the way out, a quarter at two thirds
     * ({@link TimelineSoundInstance#falloff}). The vanilla engine's own falloff is fixed at a sound file's 16 blocks,
     * too short for an effect-sized sound. Ignored while {@link #attenuation()} is off.
     */
    public float range() {
        return range;
    }

    /** Sets {@link #range()}, clamped to {@link #MIN_RANGE}..{@link #MAX_RANGE}; a non-finite value is the default. */
    public AudioClip range(float range) {
        this.range = Float.isFinite(range) ? net.minecraft.util.Mth.clamp(range, MIN_RANGE, MAX_RANGE) : DEFAULT_RANGE;
        return this;
    }

    @Override
    public AudioClip copy() {
        var clip = new AudioClip(start(), duration(), speed());
        clip.targetId(targetId()).seed(seed()).randomSeed(randomSeed());
        clip.sound = sound;
        clip.volume = volume.copy();
        clip.pitch = pitch.copy();
        clip.category = category;
        clip.loop = loop;
        clip.attenuation = attenuation;
        clip.range = range;
        return clip;
    }
}

