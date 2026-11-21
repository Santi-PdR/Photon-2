package com.lowdragmc.photon.client.fx.timeline;

import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * Audio track. Like {@link SignalTrack} it needs no target to function; its lane holds {@link AudioClip}s
 * that each play a registered {@code SoundEvent} for their span (see {@link TimelinePlayer#applyAudio}).
 * The base {@link Track#targetId} is optional here — a 3D clip plays from the bound object, or from the
 * effect's root when none is bound (where the effect is: the entity or block it was started on).
 * <p>
 * Besides its clips the track holds one sound that is not on the timeline: what plays, once, when the
 * effect is removed ({@link #onRemove()}) — how an aura gets its ending sound without a release section
 * long enough to hold it.
 */
@OnlyIn(Dist.CLIENT)
public class AudioTrack extends Track {

    /** The sound played once when the effect is removed, while {@link #onRemoveEnabled()}; never null. */
    private AudioClip onRemove = newOnRemove();
    private boolean onRemoveEnabled = false;

    public AudioTrack() {
    }

    /** The "on remove" sound's settings (sound, volume, pitch, category, 3D). Its loop flag is ignored: it plays once. */
    public AudioClip onRemove() {
        return onRemove;
    }

    public AudioTrack onRemove(AudioClip onRemove) {
        this.onRemove = onRemove == null ? newOnRemove() : onRemove.loop(false);
        return this;
    }

    /** Whether removing the effect plays {@link #onRemove()}. Off by default: an existing project stays silent. */
    public boolean onRemoveEnabled() {
        return onRemoveEnabled;
    }

    public AudioTrack onRemoveEnabled(boolean onRemoveEnabled) {
        this.onRemoveEnabled = onRemoveEnabled;
        return this;
    }

    private static AudioClip newOnRemove() {
        return new AudioClip(0, 1, 1).loop(false);
    }

    @Override
    protected Clip createClip(double start, double duration, float speed) {
        return new AudioClip(start, duration, speed);
    }

    @Override
    protected void writeClipExtra(Clip clip, CompoundTag clipTag, HolderLookup.Provider provider) {
        if (clip instanceof AudioClip audio) {
            writeAudio(audio, clipTag);
        }
    }

    @Override
    protected void readClipExtra(Clip clip, CompoundTag clipTag, HolderLookup.Provider provider) {
        if (clip instanceof AudioClip audio) {
            readAudio(audio, clipTag);
        }
    }

    @Override
    protected void writeExtra(CompoundTag tag, HolderLookup.Provider provider) {
        // only written once set up, so an untouched project saves the same bytes as before the setting existed
        if (onRemoveEnabled) {
            var removeTag = new CompoundTag();
            writeAudio(onRemove, removeTag);
            tag.put("onRemove", removeTag);
        }
    }

    @Override
    protected void readExtra(CompoundTag tag, HolderLookup.Provider provider) {
        onRemove = newOnRemove();
        onRemoveEnabled = tag.contains("onRemove", Tag.TAG_COMPOUND);
        if (onRemoveEnabled) {
            readAudio(onRemove, tag.getCompound("onRemove"));
            onRemove.loop(false);
        }
    }

    @Override
    protected void copyExtra(Track target) {
        if (target instanceof AudioTrack audio) {
            audio.onRemove = onRemove.copy().loop(false);
            audio.onRemoveEnabled = onRemoveEnabled;
        }
    }

    /** The sound settings of a clip (not its placement), as {@link #readAudio} reads them back. */
    static void writeAudio(AudioClip audio, CompoundTag clipTag) {
        clipTag.putString("sound", audio.sound().toString());
        clipTag.put("volume", audio.volume().serializeWrapper());
        clipTag.put("pitch", audio.pitch().serializeWrapper());
        clipTag.putString("category", audio.category().name());
        clipTag.putBoolean("attenuation", audio.attenuation());
        clipTag.putFloat("range", audio.range());
        clipTag.putBoolean("loop", audio.loop());
    }

    static void readAudio(AudioClip audio, CompoundTag clipTag) {
        if (clipTag.contains("sound")) {
            var loc = ResourceLocation.tryParse(clipTag.getString("sound"));
            audio.sound(loc != null ? loc : AudioClip.DEFAULT_SOUND);
        }
        audio.volume(readEnvelope(clipTag, "volume"));
        audio.pitch(readEnvelope(clipTag, "pitch"));
        if (clipTag.contains("category")) audio.category(parseCategory(clipTag.getString("category")));
        // a clip saved before the flag existed was never positional; keep it so
        audio.attenuation(clipTag.getBoolean("attenuation"));
        // a clip saved before the range existed carried the engine's own 16 blocks: the default is wider on
        // purpose, as that was the complaint, and a 3D clip from then on gets the wider reach too
        audio.range(clipTag.contains("range", Tag.TAG_FLOAT) ? clipTag.getFloat("range") : AudioClip.DEFAULT_RANGE);
        audio.loop(!clipTag.contains("loop") || clipTag.getBoolean("loop"));
    }

    /** Volume/pitch under the same key in two shapes: pre-curve clips stored a bare float, which
     *  reads back as the constant envelope it always was. */
    private static NumberFunction readEnvelope(CompoundTag clipTag, String key) {
        if (clipTag.contains(key, Tag.TAG_COMPOUND)) {
            return NumberFunction.deserializeWrapper(clipTag.getCompound(key));
        }
        if (clipTag.contains(key)) {
            return NumberFunction.constant(clipTag.getFloat(key));
        }
        return NumberFunction.constant(1);
    }

    private static SoundSource parseCategory(String name) {
        try {
            return SoundSource.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SoundSource.MASTER;
        }
    }
}
