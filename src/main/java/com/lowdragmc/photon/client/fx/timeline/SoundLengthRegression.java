package com.lowdragmc.photon.client.fx.timeline;

import com.lowdragmc.photon.Photon;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.TimeUnit;

/**
 * The sound length behind "import a sound as a clip": the clip must come out as long as the sound. The
 * length is read off-thread and {@link SoundLengthCache#getTicks} answers 0 until the read is done, which
 * right after a resource reload (the one that registers the imported sound, and which forgets every length)
 * is always — so every imported clip took the default length. The importer now waits on
 * {@link SoundLengthCache#ticksWhenKnown}; this checks that it is worth waiting for.
 */
public final class SoundLengthRegression {
    private SoundLengthRegression() {
    }

    public static void verify() throws Exception {
        // a sound every client has, registered since the sound manager loaded: measurable, and short
        var sound = AudioClip.DEFAULT_SOUND;

        // what the importer used to do: ask once right after a reload forgot everything
        SoundLengthCache.clear();
        double firstAsk = SoundLengthCache.getTicks(sound);

        // what it does now: wait for the measurement, which answers the real length
        double measured = SoundLengthCache.ticksWhenKnown(sound).get(60, TimeUnit.SECONDS);
        if (!(measured > 0)) {
            throw new IllegalStateException("the measured length of " + sound + " is " + measured + " ticks; expected > 0");
        }
        // and from then on the synchronous answer is the same, so the clip and its loop marks agree
        double afterwards = SoundLengthCache.getTicks(sound);
        if (afterwards != measured) {
            throw new IllegalStateException("after the measurement getTicks answers " + afterwards + " instead of " + measured);
        }
        var channels = SoundLengthCache.getChannels(sound);
        if (channels < 1 || channels > 2) {
            throw new IllegalStateException("the channel count of " + sound + " reads as " + channels);
        }

        // a reload while a read is in flight (the import case: the read may start before the reload applies) must
        // not hand out what the old resources said, nor hang: the read is repeated and still completes
        SoundLengthCache.clear();
        var acrossReload = SoundLengthCache.ticksWhenKnown(sound);
        SoundLengthCache.clear();
        double remeasured = acrossReload.get(60, TimeUnit.SECONDS);
        if (remeasured != measured) {
            throw new IllegalStateException("a read crossing a reload answered " + remeasured + " instead of " + measured);
        }
        // whichever side of the second reload that read landed on, waiting once more leaves the length cached
        SoundLengthCache.ticksWhenKnown(sound).get(60, TimeUnit.SECONDS);
        if (SoundLengthCache.getTicks(sound) != measured) {
            throw new IllegalStateException("the length re-read after a reload is not cached: "
                    + SoundLengthCache.getTicks(sound) + " instead of " + measured);
        }

        // a sound nobody registered completes with 0 (the importer then falls back to the default length) instead
        // of waiting forever
        var missing = new ResourceLocation("photon", "ci/no_such_sound");
        double unknown = SoundLengthCache.ticksWhenKnown(missing).get(60, TimeUnit.SECONDS);
        if (unknown != 0) {
            throw new IllegalStateException("an unregistered sound measures " + unknown + " ticks instead of 0");
        }

        Photon.LOGGER.info("sound length smoke: {} measures {} ticks, {} channels; the first synchronous ask answered {}",
                sound, measured, channels, firstAsk);
        Photon.LOGGER.info("[sound-length-smoke] an imported sound reports its measured length once asked to wait for it instead of 0 on the first ask");
    }
}
