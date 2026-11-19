package com.lowdragmc.photon.client.fx.timeline;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Converts user audio into Ogg Vorbis so any clip source can travel inside a Photon FX.
 * Minecraft only plays Ogg Vorbis sounds, but users pick whatever they have: an mp3, the
 * soundtrack of an mp4, a wav, an m4a… This decodes them to 16-bit little-endian PCM and
 * re-encodes with the pure-Java jVorbisEnc port of libvorbis — no ffmpeg, no natives.
 *
 * <p>Supported: {@code .ogg} (validated passthrough), {@code .mp3}, {@code .wav},
 * {@code .aif/.aiff}, {@code .mp4/.m4a} (AAC audio track), {@code .aac} (ADTS).
 */
public final class AudioConverter {
    /** Hard ceiling on decoded channels; anything above this is rejected instead of guessing. */
    private static final int MAX_CHANNELS = 8;

    private AudioConverter() {
    }

    /** Decoded audio, 16-bit little-endian interleaved PCM. */
    public interface PcmSource extends AutoCloseable {
        int channels();

        int sampleRate();

        /** Fills {@code dst[start, start + length)} with PCM bytes; returns the count, or {@code -1} at the end. */
        int read(byte[] dst, int start, int length) throws IOException;

        @Override
        void close() throws IOException;
    }

    /** True when the file already is an Ogg stream (magic {@code OggS}). */
    public static boolean isOggFile(File source) {
        if (source == null || !source.isFile() || source.length() < 4) return false;
        try (var input = Files.newInputStream(source.toPath())) {
            return java.util.Arrays.equals(input.readNBytes(4), new byte[]{'O', 'g', 'g', 'S'});
        } catch (IOException failure) {
            return false;
        }
    }

    /** File extensions the converter accepts, for file pickers. */
    public static String[] supportedSuffixes() {
        return new String[]{".ogg", ".oga", ".mp3", ".wav", ".aif", ".aiff", ".mp4", ".m4a", ".aac"};
    }

    /**
     * Converts {@code source} to a temporary .ogg file and returns its path. The caller owns
     * (and must delete) the result. Already-Ogg inputs are copied and validated, not re-encoded.
     */
    public static Path convertToOgg(File source) throws IOException {
        if (source == null || !source.isFile()) throw new IOException("Select an audio file");
        var temp = Files.createTempFile("photon-audio-", ".ogg");
        boolean done = false;
        try {
            try (var out = Files.newOutputStream(temp)) {
                toOgg(source, out);
            }
            if (!isOggFile(temp.toFile())) {
                throw new IOException("The audio conversion did not produce a valid OGG stream");
            }
            done = true;
            return temp;
        } finally {
            if (!done) Files.deleteIfExists(temp);
        }
    }

    /** Converts {@code source} to an Ogg Vorbis stream written to {@code out}. */
    public static void toOgg(File source, OutputStream out) throws IOException {
        if (source == null || !source.isFile()) throw new IOException("Select an audio file");
        if (isOggFile(source)) { // already playable by Minecraft — copy it through untouched
            try (var in = Files.newInputStream(source.toPath())) {
                in.transferTo(out);
            }
            return;
        }
        PcmSource pcm = openSource(source);
        try {
            OggVorbisWriter.encode(pcm.channels() > 2 ? new DownmixSource(pcm) : pcm, out);
        } finally {
            pcm.close();
        }
    }

    // ------------------------------------------------------------------ decode sources

    private static PcmSource openSource(File file) throws IOException {
        var name = file.getName().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".mp3")) return new Mp3Source(file);
            if (name.endsWith(".wav") || name.endsWith(".aif") || name.endsWith(".aiff")) return javasoundSource(file);
            if (name.endsWith(".mp4") || name.endsWith(".m4a")) return new Mp4Source(file);
            if (name.endsWith(".aac")) return new AdtsSource(file);
        } catch (IOException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IOException("Could not decode " + file.getName() + ": " + failure, failure);
        }
        throw new IOException("Photon cannot convert " + file.getName()
                + " — supported: .ogg, .mp3, .wav, .aiff, .mp4, .m4a, .aac");
    }

    private static PcmSource javasoundSource(File file) throws IOException {
        AudioInputStream audio;
        try {
            audio = AudioSystem.getAudioInputStream(file);
        } catch (Exception failure) {
            throw new IOException("Could not read " + file.getName() + " as audio: " + failure, failure);
        }
        var format = audio.getFormat();
        if (format.getChannels() < 1 || format.getChannels() > MAX_CHANNELS || format.getSampleRate() <= 0) {
            closeQuietly(audio);
            throw new IOException(file.getName() + " has an unsupported audio format (" + format + ")");
        }
        var pcmFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, format.getSampleRate(), 16,
                format.getChannels(), format.getChannels() * 2, format.getSampleRate(), false);
        AudioInputStream pcm;
        if (format.matches(pcmFormat)) {
            pcm = audio;
        } else {
            try {
                pcm = AudioSystem.getAudioInputStream(pcmFormat, audio);
            } catch (Exception failure) {
                closeQuietly(audio);
                throw new IOException("Could not convert " + file.getName() + " to PCM: " + failure, failure);
            }
        }
        return new StreamPcmSource(pcm, format.getChannels(), (int) format.getSampleRate());
    }

    /** Decodes mp3 with JLayer (pure Java). */
    private static final class Mp3Source implements PcmSource {
        private final javazoom.jl.decoder.Bitstream bitstream;
        private final javazoom.jl.decoder.Decoder decoder = new javazoom.jl.decoder.Decoder();
        private final int channels;
        private final int sampleRate;
        private byte[] pending = new byte[0];
        private int offset;

        Mp3Source(File file) throws IOException {
            bitstream = new javazoom.jl.decoder.Bitstream(new BufferedInputStream(Files.newInputStream(file.toPath())));
            var first = nextFrame(file.getName());
            if (first == null) throw new IOException(file.getName() + " has no decodable MP3 frames");
            channels = first.getChannelCount();
            sampleRate = first.getSampleFrequency();
            pending = toLittleEndian(first);
        }

        private javazoom.jl.decoder.SampleBuffer nextFrame(String name) throws IOException {
            try {
                return decoder.decodeNextFrame(bitstream);
            } catch (Exception failure) {
                throw new IOException("Could not decode MP3 frames of " + name + ": " + failure, failure);
            }
        }

        private static byte[] toLittleEndian(javazoom.jl.decoder.SampleBuffer buffer) {
            short[] samples = buffer.getBuffer();
            int length = buffer.getBufferLength();
            byte[] out = new byte[length * 2];
            for (int i = 0; i < length; i++) {
                short sample = samples[i];
                out[i * 2] = (byte) (sample & 0xFF);
                out[i * 2 + 1] = (byte) ((sample >>> 8) & 0xFF);
            }
            // JLayer's SampleBuffer accumulates across frames unless cleared; the next
            // decodeNextFrame would otherwise append past the end of the buffer.
            buffer.clearBuffer();
            return out;
        }

        @Override
        public int channels() {
            return channels;
        }

        @Override
        public int sampleRate() {
            return sampleRate;
        }

        @Override
        public int read(byte[] dst, int start, int length) throws IOException {
            while (offset >= pending.length) {
                var frame = nextFrame("stream");
                if (frame == null) return -1;
                pending = toLittleEndian(frame);
                offset = 0;
            }
            int count = Math.min(length, pending.length - offset);
            System.arraycopy(pending, offset, dst, start, count);
            offset += count;
            return count;
        }

        @Override
        public void close() throws IOException {
            try {
                bitstream.close();
            } catch (Exception failure) {
                // jLayer's Bitstream.close throws a checked JavaLayer exception, not IOException
                throw new IOException("Could not close the MP3 stream: " + failure, failure);
            }
        }
    }

    /** Extracts the AAC audio track of an mp4/m4a with JAAD (pure Java). */
    private static final class Mp4Source implements PcmSource {
        private final java.io.RandomAccessFile fileHandle;
        private final net.sourceforge.jaad.mp4.api.AudioTrack track;
        private final net.sourceforge.jaad.aac.Decoder decoder;
        private final net.sourceforge.jaad.aac.SampleBuffer buffer = new net.sourceforge.jaad.aac.SampleBuffer();
        private final int channels;
        private final int sampleRate;
        private byte[] pending = new byte[0];
        private int offset;

        Mp4Source(File file) throws IOException {
            try {
                fileHandle = new java.io.RandomAccessFile(file, "r");
                var container = new net.sourceforge.jaad.mp4.MP4Container(fileHandle);
                var movie = container.getMovie();
                List<net.sourceforge.jaad.mp4.api.Track> tracks = movie == null
                        ? List.<net.sourceforge.jaad.mp4.api.Track>of()
                        : movie.getTracks(net.sourceforge.jaad.mp4.api.AudioTrack.AudioCodec.AAC);
                if (tracks.isEmpty()) {
                    throw new IOException(file.getName() + " has no AAC audio track to take the sound from");
                }
                track = (net.sourceforge.jaad.mp4.api.AudioTrack) tracks.get(0);
                decoder = new net.sourceforge.jaad.aac.Decoder(track.getDecoderSpecificInfo());
                channels = track.getChannelCount();
                sampleRate = (int) track.getSampleRate();
            } catch (IOException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IOException("Could not open " + file.getName() + " as MP4: " + failure, failure);
            }
        }

        @Override
        public int channels() {
            return channels;
        }

        @Override
        public int sampleRate() {
            return sampleRate;
        }

        @Override
        public int read(byte[] dst, int start, int length) throws IOException {
            while (offset >= pending.length) {
                try {
                    if (!track.hasMoreFrames()) return -1;
                    var frame = track.readNextFrame();
                    if (frame == null) return -1;
                    decoder.decodeFrame(frame.getData(), buffer);
                    if (buffer.isBigEndian()) buffer.setBigEndian(false);
                    pending = buffer.getData();
                    offset = 0;
                    if (pending.length == 0) continue;
                } catch (Exception failure) {
                    throw new IOException("Could not decode AAC audio: " + failure, failure);
                }
            }
            int count = Math.min(length, pending.length - offset);
            System.arraycopy(pending, offset, dst, start, count);
            offset += count;
            return count;
        }

        @Override
        public void close() throws IOException {
            fileHandle.close();
        }
    }

    /** Decodes raw ADTS .aac streams with JAAD. */
    private static final class AdtsSource implements PcmSource {
        private final InputStream input;
        private final net.sourceforge.jaad.adts.ADTSDemultiplexer demuxer;
        private final net.sourceforge.jaad.aac.Decoder decoder;
        private final net.sourceforge.jaad.aac.SampleBuffer buffer = new net.sourceforge.jaad.aac.SampleBuffer();
        private final int channels;
        private final int sampleRate;
        private byte[] pending = new byte[0];
        private int offset;

        AdtsSource(File file) throws IOException {
            input = new BufferedInputStream(Files.newInputStream(file.toPath()));
            demuxer = new net.sourceforge.jaad.adts.ADTSDemultiplexer(input);
            decoder = new net.sourceforge.jaad.aac.Decoder(demuxer.getDecoderSpecificInfo());
            pending = decodeNext(file.getName());
            if (pending == null) throw new IOException(file.getName() + " has no decodable AAC frames");
            channels = buffer.getChannels();
            sampleRate = buffer.getSampleRate();
        }

        private byte[] decodeNext(String name) throws IOException {
            try {
                byte[] frame;
                do {
                    frame = demuxer.readNextFrame();
                    if (frame == null) return null;
                    decoder.decodeFrame(frame, buffer);
                    if (buffer.isBigEndian()) buffer.setBigEndian(false);
                } while (buffer.getData().length == 0);
                return buffer.getData();
            } catch (Exception failure) {
                throw new IOException("Could not decode AAC frames of " + name + ": " + failure, failure);
            }
        }

        @Override
        public int channels() {
            return channels;
        }

        @Override
        public int sampleRate() {
            return sampleRate;
        }

        @Override
        public int read(byte[] dst, int start, int length) throws IOException {
            while (offset >= pending.length) {
                pending = decodeNext("stream");
                if (pending == null) return -1;
                offset = 0;
            }
            int count = Math.min(length, pending.length - offset);
            System.arraycopy(pending, offset, dst, start, count);
            offset += count;
            return count;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }

    /** 16-bit LE PCM straight from a JavaSound stream (wav/aiff). */
    private static final class StreamPcmSource implements PcmSource {
        private final AudioInputStream stream;
        private final int channels;
        private final int sampleRate;

        StreamPcmSource(AudioInputStream stream, int channels, int sampleRate) {
            this.stream = stream;
            this.channels = channels;
            this.sampleRate = sampleRate;
        }

        @Override
        public int channels() {
            return channels;
        }

        @Override
        public int sampleRate() {
            return sampleRate;
        }

        @Override
        public int read(byte[] dst, int start, int length) throws IOException {
            return stream.read(dst, start, length);
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    /** Folds surround/extra channels down to stereo: left averages even sources, right odd ones. */
    private static final class DownmixSource implements PcmSource {
        private final PcmSource delegate;
        private final int sourceChannels;

        DownmixSource(PcmSource delegate) {
            this.delegate = delegate;
            this.sourceChannels = delegate.channels();
        }

        @Override
        public int channels() {
            return 2;
        }

        @Override
        public int sampleRate() {
            return delegate.sampleRate();
        }

        @Override
        public int read(byte[] dst, int start, int length) throws IOException {
            int frames = length / 4;
            if (frames <= 0) return 0;
            byte[] chunk = new byte[frames * sourceChannels * 2];
            int read = readFully(delegate, chunk);
            if (read <= 0) return -1;
            int complete = read / (sourceChannels * 2);
            for (int frame = 0; frame < complete; frame++) {
                int left = 0;
                int right = 0;
                int leftSources = 0;
                int rightSources = 0;
                for (int channel = 0; channel < sourceChannels; channel++) {
                    int index = (frame * sourceChannels + channel) * 2;
                    int sample = (short) ((chunk[index] & 0xFF) | (chunk[index + 1] << 8));
                    if ((channel & 1) == 0) {
                        left += sample;
                        leftSources++;
                    } else {
                        right += sample;
                        rightSources++;
                    }
                }
                writeShort(dst, start + frame * 4, clamp(left / Math.max(1, leftSources)));
                writeShort(dst, start + frame * 4 + 2, clamp(right / Math.max(1, rightSources)));
            }
            return complete * 4;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Reads as much as possible into {@code buffer}; returns the byte count ({@code -1} when nothing). */
    static int readFully(PcmSource source, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = source.read(buffer, total, buffer.length - total);
            if (read <= 0) break;
            total += read;
        }
        return total <= 0 ? -1 : total;
    }

    private static void writeShort(byte[] dst, int offset, int value) {
        dst[offset] = (byte) (value & 0xFF);
        dst[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static int clamp(int value) {
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // best effort
        }
    }
}
