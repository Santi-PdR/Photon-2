import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import com.lowdragmc.photon.internal.embedded.biniu.ogg.Packet;
import com.lowdragmc.photon.internal.embedded.biniu.ogg.Page;
import com.lowdragmc.photon.internal.embedded.biniu.ogg.StreamState;
import com.lowdragmc.photon.internal.embedded.biniu.vorbis.Block;
import com.lowdragmc.photon.internal.embedded.biniu.vorbis.Comment;
import com.lowdragmc.photon.internal.embedded.biniu.vorbis.DspState;
import com.lowdragmc.photon.internal.embedded.biniu.vorbis.Info;
import com.lowdragmc.photon.internal.embedded.biniu.vorbis.VorbisEnc;
import com.lowdragmc.photon.internal.embedded.jaad.adts.ADTSDemultiplexer;
import com.lowdragmc.photon.internal.embedded.jaad.mp4.MP4Container;
import com.lowdragmc.photon.internal.embedded.jaad.mp4.api.AudioTrack;
import com.lowdragmc.photon.internal.embedded.jaad.mp4.api.Movie;
import com.lowdragmc.photon.internal.embedded.jaad.mp4.api.Track;
import com.lowdragmc.photon.internal.embedded.javazoom.jl.decoder.Bitstream;
import com.lowdragmc.photon.internal.embedded.javazoom.jl.decoder.Header;

/**
 * CI-only: runs the audio codecs the way the distributable carries them. Photon's own code drives
 * jLayer, JAAD and jVorbisEnc under their original package names, which shadowJar rewrites to
 * {@code com.lowdragmc.photon.internal.embedded} when the mod jar is packaged; the dev-run smoke tests
 * therefore only ever see the original libraries. This program is compiled against the finished -all
 * jar alone and decodes an mp3, an m4a and an ADTS aac with the relocated decoders, then encodes the
 * result with the relocated Vorbis encoder the way AudioConverter does (mono, quality 0.3 with the same
 * fallbacks), so a relocation that left a reference, a resource ({@code javazoom/jl/decoder/*.ser}) or
 * a class behind fails the build instead of the first import in the editor. Every stage runs even if an
 * earlier one failed, so one run reports everything that is wrong.
 *
 * <p>Usage: {@code java RelocatedCodecsCheck <dir>} with {@code beat.mp3}, {@code beat.m4a} and
 * {@code beat.aac} in that directory; writes {@code beat-notags.m4a} and {@code relocated.ogg} next to
 * them.
 */
public final class RelocatedCodecsCheck {
    private record Pcm(int channels, int sampleRate, byte[] data, int frames) {
        double seconds() {
            return data.length / (double) (channels * 2 * sampleRate);
        }
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        for (String original : List.of("net.sourceforge.jaad.aac.Decoder", "javazoom.jl.decoder.Decoder", "biniu.vorbis.VorbisEnc")) {
            try {
                Class.forName(original);
                throw new IllegalStateException(original + " is reachable under its original package: the distributable still carries an unrelocated copy");
            } catch (ClassNotFoundException expected) {
                // the only acceptable answer
            }
        }
        int failures = 0;
        var summary = new StringBuilder("relocated codecs:");
        Pcm mp3 = null;
        for (String stage : List.of("mp3", "m4a", "aac", "ogg")) {
            try {
                switch (stage) {
                    case "mp3" -> mp3 = checkDecoded(stage, decodeMp3(dir.resolve("beat.mp3")), summary);
                    case "m4a" -> checkDecoded(stage, decodeMp4(withoutTags(dir.resolve("beat.m4a"))), summary);
                    case "aac" -> checkDecoded(stage, decodeAdts(dir.resolve("beat.aac")), summary);
                    default -> {
                        require(mp3 != null, "nothing to encode: the mp3 stage failed");
                        byte[] ogg = encodeVorbis(mono(mp3));
                        require(ogg.length > 2000 && ogg[0] == 'O' && ogg[1] == 'g' && ogg[2] == 'g' && ogg[3] == 'S',
                                "the Vorbis encoder produced " + ogg.length + " bytes that do not start an Ogg stream");
                        Files.write(dir.resolve("relocated.ogg"), ogg);
                        summary.append(String.format(" ogg %d bytes", ogg.length));
                    }
                }
            } catch (Throwable failure) {
                failures++;
                System.out.println("STAGE FAILED: " + stage + ": " + failure);
                failure.printStackTrace(System.out);
            }
        }
        require(failures == 0, failures + " stage(s) failed");
        System.out.println(summary);
    }

    private static Pcm checkDecoded(String stage, Pcm decoded, StringBuilder summary) {
        require(decoded.channels() == 2 && decoded.sampleRate() == 44100,
                stage + ": unexpected format " + decoded.channels() + " ch @ " + decoded.sampleRate() + " Hz");
        require(decoded.seconds() >= 0.4 && decoded.seconds() <= 0.7,
                stage + ": decoded " + decoded.seconds() + " s of a 0.5 s fixture");
        summary.append(String.format(" %s %d frames %.3f s,", stage, decoded.frames(), decoded.seconds()));
        return decoded;
    }

    /** AudioConverter.MonoSource: the average of all channels per frame, 16-bit little-endian. */
    private static Pcm mono(Pcm pcm) {
        int frames = pcm.data().length / (pcm.channels() * 2);
        byte[] out = new byte[frames * 2];
        for (int frame = 0; frame < frames; frame++) {
            int sum = 0;
            for (int channel = 0; channel < pcm.channels(); channel++) {
                int index = (frame * pcm.channels() + channel) * 2;
                sum += (short) ((pcm.data()[index] & 0xFF) | (pcm.data()[index + 1] << 8));
            }
            int sample = sum / pcm.channels();
            out[frame * 2] = (byte) (sample & 0xFF);
            out[frame * 2 + 1] = (byte) ((sample >>> 8) & 0xFF);
        }
        return new Pcm(1, pcm.sampleRate(), out, frames);
    }

    /** jLayer, as AudioConverter.Mp3Source drives it (own SampleBuffer, decodeFrame per header). */
    private static Pcm decodeMp3(Path file) throws Exception {
        var bitstream = new Bitstream(new BufferedInputStream(Files.newInputStream(file)));
        try {
            var decoder = new com.lowdragmc.photon.internal.embedded.javazoom.jl.decoder.Decoder();
            Header header = bitstream.readFrame();
            require(header != null, file.getFileName() + " has no decodable MP3 frames");
            int channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
            int sampleRate = header.frequency();
            var buffer = new com.lowdragmc.photon.internal.embedded.javazoom.jl.decoder.SampleBuffer(sampleRate, channels);
            decoder.setOutputBuffer(buffer);
            var pcm = new ByteArrayOutputStream();
            int frames = 0;
            while (header != null) {
                decoder.decodeFrame(header, bitstream);
                bitstream.closeFrame();
                short[] samples = buffer.getBuffer();
                int length = buffer.getBufferLength();
                for (int i = 0; i < length; i++) {
                    pcm.write(samples[i] & 0xFF);
                    pcm.write((samples[i] >>> 8) & 0xFF);
                }
                buffer.clearBuffer();
                frames++;
                header = bitstream.readFrame();
            }
            return new Pcm(channels, sampleRate, pcm.toByteArray(), frames);
        } finally {
            bitstream.close();
        }
    }

    /**
     * ffmpeg writes iTunes tags ({@code moov > udta > meta > ilst}) that JAAD's box walker misparses
     * ("box too large for parent") before it reaches the audio track; AudioConverter strips {@code udta}
     * and {@code meta} before opening an MP4, so the check hands JAAD the same box subset. {@code moov}
     * follows {@code mdat} in ffmpeg's default layout, so shrinking it leaves the chunk offsets valid.
     */
    private static Path withoutTags(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        var out = new ByteArrayOutputStream(data.length);
        copyBoxes(data, 0, data.length, out);
        Path stripped = file.resolveSibling("beat-notags.m4a");
        Files.write(stripped, out.toByteArray());
        return stripped;
    }

    /** Copies the boxes in [from, to), dropping udta/meta and rewriting moov/trak around what remains. */
    private static void copyBoxes(byte[] buf, int from, int to, ByteArrayOutputStream out) throws IOException {
        int i = from;
        while (i + 8 <= to) {
            long size = ByteBuffer.wrap(buf, i, 4).getInt() & 0xFFFFFFFFL;
            String type = new String(buf, i + 4, 4, StandardCharsets.ISO_8859_1);
            int header = 8;
            if (size == 1 && i + 16 <= to) {
                size = ByteBuffer.wrap(buf, i + 8, 8).getLong();
                header = 16;
            } else if (size == 0) {
                size = to - i; // box to the end of the file
            }
            long end = i + size;
            if (size < header || end > to) throw new IOException("malformed box " + type + " at " + i);
            if (type.equals("udta") || type.equals("meta")) {
                // dropped, as AudioConverter.scrubMp4Metadata does
            } else if (type.equals("moov") || type.equals("trak")) {
                var inner = new ByteArrayOutputStream((int) (end - i));
                copyBoxes(buf, i + header, (int) end, inner);
                byte[] body = inner.toByteArray();
                out.write(ByteBuffer.allocate(4).putInt(body.length + 8).array());
                out.write(type.getBytes(StandardCharsets.ISO_8859_1));
                out.write(body);
            } else {
                out.write(buf, i, (int) (end - i));
            }
            i = (int) end;
        }
        if (i < to) out.write(buf, i, to - i);
    }

    /** JAAD's MP4 demuxer and AAC decoder, as AudioConverter.Mp4Source drives them. */
    private static Pcm decodeMp4(Path file) throws Exception {
        try (var handle = new RandomAccessFile(file.toFile(), "r")) {
            var container = new MP4Container(handle);
            Movie movie = container.getMovie();
            require(movie != null, file.getFileName() + " has no movie box");
            List<Track> tracks = movie.getTracks(AudioTrack.AudioCodec.AAC);
            require(!tracks.isEmpty(), file.getFileName() + " has no AAC audio track");
            var track = (AudioTrack) tracks.get(0);
            var decoder = new com.lowdragmc.photon.internal.embedded.jaad.aac.Decoder(track.getDecoderSpecificInfo());
            var buffer = new com.lowdragmc.photon.internal.embedded.jaad.aac.SampleBuffer();
            var pcm = new ByteArrayOutputStream();
            int frames = 0;
            while (track.hasMoreFrames()) {
                var frame = track.readNextFrame();
                if (frame == null) break;
                decoder.decodeFrame(frame.getData(), buffer);
                if (buffer.isBigEndian()) buffer.setBigEndian(false);
                pcm.write(buffer.getData());
                frames++;
            }
            return new Pcm(track.getChannelCount(), (int) track.getSampleRate(), pcm.toByteArray(), frames);
        }
    }

    /** JAAD's ADTS demuxer and AAC decoder, as AudioConverter.AdtsSource drives them. */
    private static Pcm decodeAdts(Path file) throws Exception {
        try (var input = new BufferedInputStream(Files.newInputStream(file))) {
            var demuxer = new ADTSDemultiplexer(input);
            var decoder = new com.lowdragmc.photon.internal.embedded.jaad.aac.Decoder(demuxer.getDecoderSpecificInfo());
            var buffer = new com.lowdragmc.photon.internal.embedded.jaad.aac.SampleBuffer();
            var pcm = new ByteArrayOutputStream();
            int frames = 0;
            try {
                byte[] frame;
                while ((frame = demuxer.readNextFrame()) != null) {
                    decoder.decodeFrame(frame, buffer);
                    if (buffer.isBigEndian()) buffer.setBigEndian(false);
                    pcm.write(buffer.getData());
                    frames++;
                }
            } catch (EOFException end) {
                // the demuxer reports the end of a raw stream this way
            }
            require(frames > 0, file.getFileName() + " has no decodable AAC frames");
            return new Pcm(buffer.getChannels(), buffer.getSampleRate(), pcm.toByteArray(), frames);
        }
    }

    /**
     * jVorbisEnc, the loop of OggVorbisWriter.encode: default quality 0.3, then the same substitutes
     * around jVorbisEnc 0.0.2's broken psy table (an ArrayIndexOutOfBoundsException from initVBR).
     */
    private static byte[] encodeVorbis(Pcm pcm) throws Exception {
        var out = new ByteArrayOutputStream();
        Info info = null;
        Exception lastFailure = null;
        for (float attempt : new float[]{0.3f, 0.3999f, 0.8f, 0.1f}) {
            try {
                var candidate = new Info();
                candidate.init();
                int ret = new VorbisEnc().initVBR(candidate, pcm.channels(), pcm.sampleRate(), attempt);
                if (ret == 0) {
                    info = candidate;
                    System.out.printf("vorbis: quality %.4f accepted for %d channel(s) at %d Hz%n", attempt, pcm.channels(), pcm.sampleRate());
                    break;
                }
                candidate.clear();
                lastFailure = new IOException("Vorbis refused quality " + attempt + ": " + ret);
            } catch (ArrayIndexOutOfBoundsException brokenPsyTable) {
                System.out.printf("vorbis: quality %.4f hits the jVorbisEnc psy-table typo (%s), trying the next%n", attempt, brokenPsyTable.getMessage());
                lastFailure = brokenPsyTable;
            }
        }
        if (info == null) throw new IOException("Vorbis could not initialize an encoder at " + pcm.sampleRate() + " Hz", lastFailure);
        var comment = new Comment();
        comment.init();
        comment.addTag("ENCODER", "Photon relocated codec check");
        var dsp = new DspState();
        dsp.analysisInit(info);
        var block = new Block(dsp);
        block.blockInit(dsp);
        var state = new StreamState();
        state.init(new Random(7).nextInt());
        var page = new Page();
        var packet = new Packet();

        var header = new Packet();
        var headerComments = new Packet();
        var headerCodebook = new Packet();
        dsp.analysisHeaderOut(comment, header, headerComments, headerCodebook);
        state.packetIn(header);
        state.packetIn(headerComments);
        state.packetIn(headerCodebook);
        while (state.flush(page)) {
            write(out, page);
        }

        int frameBytes = pcm.channels() * 2;
        int position = 0;
        boolean eos = false;
        while (!eos) {
            int samples = Math.min(4096, (pcm.data().length - position) / frameBytes);
            if (samples <= 0) {
                dsp.analysisWrote(0); // end of stream
            } else {
                float[][] buffer = dsp.analysisBuffer(samples);
                for (int i = 0, l = dsp.pcm_current; i < samples; i++, l++) {
                    for (int channel = 0; channel < pcm.channels(); channel++) {
                        int index = position + (i * pcm.channels() + channel) * 2;
                        int sample = (short) ((pcm.data()[index] & 0xFF) | (pcm.data()[index + 1] << 8));
                        buffer[channel][l] = sample / 32768f;
                    }
                }
                dsp.analysisWrote(samples);
                position += samples * frameBytes;
            }
            while (block.analysisBlockOut()) {
                block.analysis(null);
                block.bitrateAddBlock();
                while (dsp.bitrateFlushPacket(packet)) {
                    state.packetIn(packet);
                    while (state.pageOut(page)) {
                        write(out, page);
                        if (page.eos()) eos = true;
                    }
                }
            }
        }
        state.clear();
        block.clear();
        dsp.clear();
        comment.clear();
        info.clear();
        return out.toByteArray();
    }

    private static void write(OutputStream out, Page page) throws IOException {
        out.write(page.header_base, page.header, page.header_len);
        out.write(page.body_base, page.body, page.body_len);
    }

    private static void require(boolean condition, String problem) {
        if (!condition) throw new IllegalStateException(problem);
    }
}
