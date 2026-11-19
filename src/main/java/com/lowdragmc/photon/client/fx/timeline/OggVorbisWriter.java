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
 */
final class OggVorbisWriter {
    /** VBR quality 0.4 ≈ 128 kbps stereo — plenty for game audio and music alike. */
    private static final float DEFAULT_QUALITY = 0.4f;
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
        var info = new Info();
        var comment = new Comment();
        var dsp = new DspState();
        var block = new Block(dsp);
        boolean eos = false;
        try {
            info.init();
            var encoder = new VorbisEnc();
            int ret = encoder.initVBR(info, channels, rate, quality);
            if (ret != 0) {
                throw new IOException("Vorbis refused to encode " + channels + " channel(s) at " + rate + " Hz");
            }
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
            info.clear();
        }
    }
}
