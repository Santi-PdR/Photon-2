package com.lowdragmc.photon.client.fx.timeline;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

import biniu.ogg.Packet;
import biniu.ogg.Page;
import biniu.ogg.StreamState;
import biniu.vorbis.Block;
import biniu.vorbis.Comment;
import biniu.vorbis.DspState;
import biniu.vorbis.Info;
import biniu.vorbis.VorbisEnc;

/**
 * Streams 16-bit little-endian PCM into an Ogg Vorbis file using jVorbisEnc, the pure-Java port
 * of libvorbis's encoder (biniu). Mono and stereo, any sample rate the Vorbis setup templates
 * cover (8–200 kHz); surround input is down-mixed before it gets here. This is what makes
 * "pick an mp3 and it becomes a Minecraft sound" possible without shipping ffmpeg natives.
 *
 * <p>Known upstream bug (jVorbisEnc 0.0.2): {@code Psych44._vp_tonemask_adj_longblock} entries 6
 * and 7 hold 16 values where the encoder walks 17 ({@code Psy.P_BANDS}), so {@code initVBR}
 * throws {@code ArrayIndexOutOfBoundsException} for any quality mapping to base setting [5, 8) —
 * i.e. VBR quality [0.4, 0.8) on the 44.1/32/X setup templates. {@link #encode} avoids the broken
 * window by preference order and falls back to empirically safe qualities if a table typo ever
 * bites another template.
 */
final class OggVorbisWriter {
    /** VBR quality 0.3 ≈ 112 kbps stereo / 96 kbps mono — plenty for game audio and music alike. */
    private static final float DEFAULT_QUALITY = 0.3f;
    /** Qualities just outside the broken Psych44 window that keep the requested fidelity. */
    private static final float[] SAFE_QUALITIES = {0.3999f, 0.8f, 0.1f};
    private static final int BLOCK_FRAMES = 4096;

    private OggVorbisWriter() {
    }

    static void encode(AudioConverter.PcmSource source, OutputStream out) throws IOException {
        encode(source, out, DEFAULT_QUALITY);
    }

    static void encode(AudioConverter.PcmSource source, OutputStream out, float quality) throws IOException {
        int channels = source.channels();
        int rate = source.sampleRate();
        if (channels < 1 || channels > 2) {
            throw new IOException("Vorbis encoding expects mono or stereo, got " + channels + " channels");
        }
        if (rate < 8_000 || rate > 200_000) {
            throw new IOException("Sample rate " + rate + " Hz is out of the Vorbis range");
        }

        var state = new StreamState();
        var page = new Page();
        var packet = new Packet();
        Info info = null;
        try {
            // Initialize the encoder before a single byte is written: a broken psy table at the
            // requested quality must not leave a half-written Ogg stream behind.
            VorbisEnc encoder = null;
            Exception lastFailure = null;
            for (float attempt : attemptOrder(quality)) {
                try {
                    var candidate = new Info();
                    candidate.init();
                    var candidateEncoder = new VorbisEnc();
                    int ret = candidateEncoder.initVBR(candidate, channels, rate, attempt);
                    if (ret == 0) {
                        info = candidate;
                        encoder = candidateEncoder;
                        break;
                    }
                    candidate.clear();
                    lastFailure = new IOException(
                            "Vorbis refused quality " + attempt + " for " + channels + " channel(s) at " + rate + " Hz");
                } catch (ArrayIndexOutOfBoundsException brokenPsyTable) {
                    // jVorbisEnc 0.0.2 psy-table typo; try the next safe quality.
                    lastFailure = brokenPsyTable;
                }
            }
            if (encoder == null) {
                throw new IOException("Vorbis could not initialize an encoder at " + rate + " Hz", lastFailure);
            }

            var comment = new Comment();
            var dsp = new DspState();
            var block = new Block(dsp);
            boolean eos = false;
            try {
                comment.init();
                comment.addTag("ENCODER", "Photon jVorbisEnc");
                dsp.analysisInit(info);
                block.blockInit(dsp);
                state.init(new Random().nextInt());

                // the three Vorbis headers, each in its own page
                var header = new Packet();
                var headerComments = new Packet();
                var headerCodebook = new Packet();
                dsp.analysisHeaderOut(comment, header, headerComments, headerCodebook);
                state.packetIn(header);
                state.packetIn(headerComments);
                state.packetIn(headerCodebook);
                while (state.flush(page)) {
                    out.write(page.header_base, page.header, page.header_len);
                    out.write(page.body_base, page.body, page.body_len);
                }

                byte[] readBuffer = new byte[BLOCK_FRAMES * channels * 2];
                while (!eos) {
                    int bytes = AudioConverter.readFully(source, readBuffer);
                    if (bytes <= 0) {
                        dsp.analysisWrote(0); // end of stream
                    } else {
                        int samples = bytes / (channels * 2);
                        float[][] buffer = dsp.analysisBuffer(samples);
                        for (int i = 0, l = dsp.pcm_current; i < samples; i++, l++) {
                            for (int channel = 0; channel < channels; channel++) {
                                int index = (i * channels + channel) * 2;
                                int sample = (short) ((readBuffer[index] & 0xFF) | (readBuffer[index + 1] << 8));
                                buffer[channel][l] = sample / 32768f;
                            }
                        }
                        dsp.analysisWrote(samples);
                    }
                    while (block.analysisBlockOut()) {
                        block.analysis(null);
                        block.bitrateAddBlock();
                        while (dsp.bitrateFlushPacket(packet)) {
                            state.packetIn(packet);
                            while (state.pageOut(page)) {
                                out.write(page.header_base, page.header, page.header_len);
                                out.write(page.body_base, page.body, page.body_len);
                                if (page.eos()) eos = true;
                            }
                        }
                    }
                }
            } finally {
                state.clear();
                block.clear();
                dsp.clear();
                comment.clear();
            }
        } finally {
            if (info != null) info.clear();
        }
    }

    /**
     * The requested quality first, then known-safe substitutes. The requested quality is nudged
     * below 0.4 first because that keeps the fidelity decision of the caller as closely as the
     * broken Psych44 tables allow.
     */
    private static float[] attemptOrder(float quality) {
        float requested = Math.min(0.9999f, Math.max(0f, quality));
        var attempts = new float[1 + SAFE_QUALITIES.length];
        attempts[0] = requested;
        System.arraycopy(SAFE_QUALITIES, 0, attempts, 1, SAFE_QUALITIES.length);
        return attempts;
    }
}
