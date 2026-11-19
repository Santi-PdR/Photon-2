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
            if (name.endsWith(".mp4") || name.endsWith(".m4a")) return mp4Source(file);
            if (name.endsWith(".aac")) return new AdtsSource(file);
        } catch (IOException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IOException("Could not decode " + file.getName() + ": " + failure, failure);
        }
        throw new IOException("Photon cannot convert " + file.getName()
                + " — supported: .ogg, .mp3, .wav, .aiff, .mp4, .m4a, .aac");
    }

    /** Opens the MP4/M4A through JAAD, first stripping the tag trees its box walker cannot parse. */
    private static PcmSource mp4Source(File file) throws IOException {
        Path scrubbed = scrubMp4Metadata(file);
        File open = scrubbed == null ? file : scrubbed.toFile();
        try {
            return new Mp4Source(open, scrubbed);
        } catch (IOException | RuntimeException failure) {
            if (scrubbed != null) Files.deleteIfExists(scrubbed);
            throw failure;
        }
    }

    /**
     * Returns a temp copy of the MP4 without its {@code udta}/{@code meta} tag subtrees, or
     * {@code null} when the file has none. JAAD's box walker misparses ffmpeg's iTunes-style
     * {@code moov > udta > meta > ilst} children ("box too large for parent") and gives up before
     * the audio track is read, even though the track itself is fine. Only whole {@code udta} and
     * {@code meta} boxes are dropped — {@code moov} and {@code trak} are rewritten with patched
     * sizes, every other box (including {@code mdat} and the sample tables) is copied byte for byte.
     */
    private static Path scrubMp4Metadata(File file) throws IOException {
        var temp = Files.createTempFile("photon-mp4-", ".mp4");
        boolean complete = false;
        boolean stripped;
        try (var in = new BufferedInputStream(Files.newInputStream(file.toPath()), 64 * 1024);
             var out = new java.io.BufferedOutputStream(Files.newOutputStream(temp), 64 * 1024)) {
            stripped = copyMp4WithoutTags(in, out);
            complete = true;
        } finally {
            if (!complete) Files.deleteIfExists(temp);
        }
        if (!stripped) {
            Files.deleteIfExists(temp);
            return null;
        }
        return temp;
    }

    /** Streams top-level boxes through, dropping {@code udta}/{@code meta} and rewriting {@code moov}. */
    private static boolean copyMp4WithoutTags(InputStream in, OutputStream out) throws IOException {
        boolean stripped = false;
        byte[] head = new byte[16];
        while (true) {
            int got = readUpTo(in, head, 0, 8);
            if (got <= 0) break;
            if (got < 8) { // trailing garbage — keep it rather than lose bytes
                out.write(head, 0, got);
                break;
            }
            long size = readUInt(head, 0, 4);
            String type = new String(head, 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            int header = 8;
            if (size == 1) {
                int gotLarge = readUpTo(in, head, 8, 8);
                if (gotLarge < 8) {
                    out.write(head, 0, 8 + gotLarge);
                    break;
                }
                size = readUInt(head, 8, 8);
                header = 16;
            }
            long payload = size == 0 ? Long.MAX_VALUE : size - header; // size 0 = box to EOF
            if (payload < 0) {
                out.write(head, 0, header);
                break;
            }
            if (type.equals("udta") || type.equals("meta")) {
                skipFully(in, payload);
                stripped = true;
            } else if (type.equals("moov") && payload <= 64L * 1024 * 1024) {
                var body = new byte[(int) payload];
                if (readUpTo(in, body, 0, body.length) < body.length) {
                    out.write(head, 0, header);
                    out.write(body);
                    break;
                }
                var rewritten = new java.io.ByteArrayOutputStream(body.length);
                if (rewriteContainer(body, 0, body.length, rewritten)) {
                    stripped = true;
                    writeBox(out, type, rewritten.toByteArray(), header == 16);
                } else {
                    out.write(head, 0, header);
                    out.write(body);
                }
            } else {
                out.write(head, 0, header);
                copyExactly(in, out, payload);
            }
            if (size == 0) break;
        }
        return stripped;
    }

    /** Copies one container's children, dropping {@code udta}/{@code meta}; recurses into {@code trak}. */
    private static boolean rewriteContainer(byte[] buf, int from, int to, java.io.ByteArrayOutputStream out) {
        boolean stripped = false;
        int i = from;
        while (i + 8 <= to) {
            long size = readUInt(buf, i, 4);
            String type = new String(buf, i + 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            int header = 8;
            if (size == 1 && i + 16 <= to) {
                size = readUInt(buf, i + 8, 8);
                header = 16;
            }
            long end = i + size;
            if (size < header || end > to) { // malformed tail — keep it untouched
                out.write(buf, i, to - i);
                return stripped;
            }
            if (type.equals("udta") || type.equals("meta")) {
                stripped = true;
            } else if (type.equals("trak")) {
                var inner = new java.io.ByteArrayOutputStream((int) size);
                if (rewriteContainer(buf, i + header, (int) end, inner)) {
                    stripped = true;
                    writeBox(out, type, inner.toByteArray(), header == 16);
                } else {
                    out.write(buf, i, (int) size);
                }
            } else {
                out.write(buf, i, (int) size);
            }
            i = (int) end;
        }
        if (i < to) out.write(buf, i, to - i);
        return stripped;
    }

    /** Writes a complete box: patched size, kept type, new body. */
    private static void writeBox(OutputStream out, String type, byte[] body, boolean largeSize) throws IOException {
        byte[] head = new byte[largeSize ? 16 : 8];
        if (largeSize) {
            writeUInt(head, 0, 1, 4);
            writeUInt(head, 8, 16L + body.length, 8);
        } else {
            writeUInt(head, 0, 8L + body.length, 4);
        }
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        System.arraycopy(typeBytes, 0, head, 4, 4);
        out.write(head);
        out.write(body);
    }

    private static long readUInt(byte[] buf, int offset, int length) {
        long value = 0;
        for (int i = 0; i < length; i++) value = (value << 8) | (buf[offset + i] & 0xFF);
        return value;
    }

    private static void writeUInt(byte[] buf, int offset, long value, int length) {
        for (int i = length - 1; i >= 0; i--) {
            buf[offset + i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
    }

    /** Reads until {@code length} bytes are filled or EOF; returns how many were read. */
    private static int readUpTo(InputStream in, byte[] buf, int offset, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int got = in.read(buf, offset + total, length - total);
            if (got < 0) break;
            total += got;
        }
        return total;
    }

    private static void skipFully(InputStream in, long count) throws IOException {
        while (count > 0) {
            long skipped = in.skip(count);
            if (skipped <= 0) {
                if (in.read() < 0) return;
                count--;
            } else {
                count -= skipped;
            }
        }
    }

    private static void copyExactly(InputStream in, OutputStream out, long count) throws IOException {
        byte[] chunk = new byte[64 * 1024];
        while (count > 0) {
            int got = in.read(chunk, 0, (int) Math.min(chunk.length, count));
            if (got < 0) return;
            out.write(chunk, 0, got);
            count -= got;
        }
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
        private final javazoom.jl.decoder.SampleBuffer sampleBuffer;
        private final int channels;
        private final int sampleRate;
        private byte[] pending = new byte[0];
        private int offset;

        Mp3Source(File file) throws IOException {
            bitstream = new javazoom.jl.decoder.Bitstream(new BufferedInputStream(Files.newInputStream(file.toPath())));
            var header = readFrame(file.getName());
            if (header == null) throw new IOException(file.getName() + " has no decodable MP3 frames");
            channels = header.mode() == javazoom.jl.decoder.Header.SINGLE_CHANNEL ? 1 : 2;
            sampleRate = header.frequency();
            // jlayer 1.0.6's decodeNextFrame() allocates a fresh SampleBuffer per call, but the
            // LayerIIIDecoder it caches keeps writing into the buffer it was created with — frame 2
            // then overflows frame 1's buffer (ArrayIndexOutOfBoundsException). Own the buffer
            // instead: decodeFrame() clears and refills this same one for every frame.
            sampleBuffer = new javazoom.jl.decoder.SampleBuffer(sampleRate, channels);
            decoder.setOutputBuffer(sampleBuffer);
            decodeCurrentFrame(header, file.getName());
            pending = toLittleEndian(sampleBuffer);
        }

        private javazoom.jl.decoder.Header readFrame(String name) throws IOException {
            try {
                return bitstream.readFrame();
            } catch (Exception failure) {
                throw new IOException("Could not read MP3 frames of " + name + ": " + failure, failure);
            }
        }

        private void decodeCurrentFrame(javazoom.jl.decoder.Header header, String name) throws IOException {
            try {
                decoder.decodeFrame(header, bitstream);
                bitstream.closeFrame();
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
            // Belt and braces: decodeFrame() already clears the buffer before each frame.
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
                var header = readFrame("stream");
                if (header == null) return -1;
                decodeCurrentFrame(header, "stream");
                pending = toLittleEndian(sampleBuffer);
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
        private final Path deleteOnClose;
        private final net.sourceforge.jaad.mp4.api.AudioTrack track;
        private final net.sourceforge.jaad.aac.Decoder decoder;
        private final net.sourceforge.jaad.aac.SampleBuffer buffer = new net.sourceforge.jaad.aac.SampleBuffer();
        private final int channels;
        private final int sampleRate;
        private byte[] pending = new byte[0];
        private int offset;

        Mp4Source(File file, Path deleteOnClose) throws IOException {
            this.deleteOnClose = deleteOnClose;
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
            try {
                fileHandle.close();
            } finally {
                if (deleteOnClose != null) Files.deleteIfExists(deleteOnClose);
            }
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
