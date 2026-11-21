package com.lowdragmc.photon.client.fx.timeline;

import com.google.gson.JsonParser;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.util.PhotonPaths;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.stb.STBVorbis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * The automatic audio → Ogg conversion behind "pick an mp3 (or the sound of an mp4) and use it".
 * Verifies the whole chain the way Minecraft will consume it: the converted stream must open in
 * {@link STBVorbis} (vanilla's own Ogg Vorbis decoder), be <b>mono</b> (the only kind of sound OpenAL
 * places in the world — a stereo file plays everywhere at the same volume, so 3D attenuation did nothing
 * for every sound imported stereo), carry the expected length, and decode with audible energy. CI
 * generates the mp3/m4a/mp4 fixtures with ffmpeg before the client starts; the synthesized wav path
 * always runs, so the smoke is meaningful even without fixtures.
 */
public final class AudioConversionRegression {
    private AudioConversionRegression() {
    }

    public static void verify() throws Exception {
        var gameDir = Minecraft.getInstance().gameDirectory.toPath();
        var fixtures = gameDir.resolve("photon").resolve("smoke-fixtures");
        Path work = Files.createTempDirectory("photon-audio-smoke");
        try {
            // synthesized stereo sine wav → mono ogg, and playable with the same decoder Minecraft uses
            var wav = work.resolve("tone.wav");
            writeSineWav(wav, 44100, 2, 0.3f);
            var ogg = AudioConverter.convertToOgg(wav.toFile());
            try {
                verifyOgg(ogg, 44100, 1, 0.3f, true);
                if (AudioConverter.oggChannels(ogg.toFile()) != 1) {
                    throw new IllegalStateException("the Vorbis header of a converted sound does not read as mono");
                }
            } finally {
                Files.deleteIfExists(ogg);
            }

            // a stereo .ogg (what every earlier build imported) is folded down, not passed through; a mono one is copied
            var stereoOgg = work.resolve("stereo.ogg");
            try (var pcm = AudioConverter.open(wav.toFile()); var out = Files.newOutputStream(stereoOgg)) {
                OggVorbisWriter.encode(pcm, out);
            }
            if (AudioConverter.oggChannels(stereoOgg.toFile()) != 2) {
                throw new IllegalStateException("the stereo fixture does not read as stereo: "
                        + AudioConverter.oggChannels(stereoOgg.toFile()));
            }
            var folded = AudioConverter.convertToOgg(stereoOgg.toFile());
            try {
                verifyOgg(folded, 44100, 1, 0.3f, true);
                var copied = AudioConverter.convertToOgg(folded.toFile());
                try {
                    if (!java.util.Arrays.equals(Files.readAllBytes(copied), Files.readAllBytes(folded))) {
                        throw new IllegalStateException("a mono .ogg was re-encoded instead of copied through");
                    }
                } finally {
                    Files.deleteIfExists(copied);
                }
            } finally {
                Files.deleteIfExists(folded);
            }

            // the importer registers the converted sound and the workspace copy plays
            var id = AudioAssetImporter.importSound(wav.toFile());
            var registered = PhotonPaths.getAssetsDir().toPath()
                    .resolve("photon").resolve("sounds").resolve(id.getPath() + ".ogg");
            if (!id.getNamespace().equals("photon") || !id.getPath().startsWith("user/")
                    || !Files.isRegularFile(registered)) {
                throw new IllegalStateException("importSound did not register the converted sound: " + id);
            }
            verifyOgg(registered, 44100, 1, 0.3f, true);
            var soundsJson = PhotonPaths.getAssetsDir().toPath().resolve("photon").resolve("sounds.json");
            var root = JsonParser.parseString(Files.readString(soundsJson)).getAsJsonObject();
            if (!root.has(id.getPath())) {
                throw new IllegalStateException("sounds.json is missing the imported sound " + id.getPath());
            }

            // fixture formats (generated with ffmpeg in CI; skipped when absent, e.g. local runs)
            verifyFixture(fixtures.resolve("beat.mp3"), 44100);
            verifyFixture(fixtures.resolve("beat.m4a"), 44100);
            verifyFixture(fixtures.resolve("beat.mp4"), 44100); // the sound of a video
            verifyFixture(fixtures.resolve("beat48k.wav"), 48000); // the non-44.1k Vorbis setup

            // an unsupported file must fail with the list of supported formats
            var junk = work.resolve("junk.txt");
            Files.writeString(junk, "definitely not audio");
            try {
                AudioConverter.convertToOgg(junk.toFile());
                throw new IllegalStateException("a text file must not convert to Ogg");
            } catch (IOException expected) {
                if (expected.getMessage() == null || !expected.getMessage().contains(".mp3")) {
                    throw new IllegalStateException("the error must list the supported formats: " + expected.getMessage());
                }
            }
        } finally {
            deleteRecursively(work);
        }
        Photon.LOGGER.info("[audio-conversion-smoke] user audio formats convert to playable mono Ogg Vorbis");
    }

    private static void verifyFixture(Path fixture, int expectedRate) throws IOException {
        if (!Files.isRegularFile(fixture)) {
            Photon.LOGGER.info("audio conversion fixture {} is absent; skipping its check", fixture.getFileName());
            return;
        }
        var ogg = AudioConverter.convertToOgg(fixture.toFile());
        try {
            verifyOgg(ogg, expectedRate, 1, 0.5f, true);
        } finally {
            Files.deleteIfExists(ogg);
        }
    }

    /** Opens the Ogg in stb_vorbis and checks the decoded stream matches the source closely enough. */
    private static void verifyOgg(Path ogg, int expectedRate, int expectedChannels,
                                  float expectedSeconds, boolean requireEnergy) throws IOException {
        byte[] bytes = Files.readAllBytes(ogg);
        var memory = MemoryUtil.memAlloc(Math.max(1, bytes.length)).put(bytes).flip();
        var error = MemoryUtil.memAllocInt(1);
        var decodeBuffer = MemoryUtil.memAllocShort(4096 * expectedChannels);
        long decoder = 0L;
        try {
            decoder = STBVorbis.stb_vorbis_open_memory(memory, error, null);
            if (decoder == 0L) {
                throw new IllegalStateException("Minecraft's stb_vorbis cannot open the converted OGG (error "
                        + error.get(0) + "): " + ogg);
            }
            int samples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);
            long expected = (long) (expectedRate * expectedSeconds);
            if (samples <= 0 || samples < expected * 5 / 10 || samples > expected * 16 / 10) {
                throw new IllegalStateException("converted OGG length " + samples
                        + " samples is out of range for " + expectedSeconds + " s at " + expectedRate + " Hz");
            }
            decodeBuffer.clear();
            int decoded = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, expectedChannels, decodeBuffer);
            if (decoded <= 0) {
                throw new IllegalStateException("converted OGG decoded no audio: " + ogg);
            }
            if (requireEnergy) {
                int peak = 0;
                for (int i = 0; i < decoded * expectedChannels; i++) {
                    peak = Math.max(peak, Math.abs(decodeBuffer.get(i)));
                }
                if (peak < 500) {
                    throw new IllegalStateException("converted OGG decodes as silence (peak " + peak + "): " + ogg);
                }
            }
        } finally {
            if (decoder != 0L) STBVorbis.stb_vorbis_close(decoder);
            MemoryUtil.memFree(memory);
            MemoryUtil.memFree(error);
            MemoryUtil.memFree(decodeBuffer);
        }
    }

    private static void writeSineWav(Path target, int rate, int channels, float seconds) throws IOException {
        var format = new AudioFormat(rate, 16, channels, true, false);
        int samples = (int) (rate * seconds);
        byte[] data = new byte[samples * channels * 2];
        for (int i = 0; i < samples; i++) {
            int value = (int) (Math.sin(2 * Math.PI * 440 * i / rate) * 0.5 * Short.MAX_VALUE);
            for (int channel = 0; channel < channels; channel++) {
                int index = (i * channels + channel) * 2;
                data[index] = (byte) (value & 0xFF);
                data[index + 1] = (byte) ((value >> 8) & 0xFF);
            }
        }
        var stream = new AudioInputStream(new ByteArrayInputStream(data), format, samples);
        try (var out = new ByteArrayOutputStream()) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, out);
            Files.write(target, out.toByteArray());
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
