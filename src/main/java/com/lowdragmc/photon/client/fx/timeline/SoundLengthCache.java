package com.lowdragmc.photon.client.fx.timeline;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Best-effort cache of a {@code SoundEvent}'s length in ticks and channel count, read off-thread from its
 * OGG headers so an {@link AudioClip} can draw one loop sub-division per repeat and the editor can warn
 * about a stereo sound (which the game cannot place in the world). The first lookup for an id kicks off
 * an async read and returns 0 until it resolves; a sound that can't be measured (missing / event-reference
 * / decode error) caches 0 and is not retried.
 */
@OnlyIn(Dist.CLIENT)
public class SoundLengthCache {
    /** What one OGG told us: length in ticks and channels, 0 for whatever could not be read. */
    private record Info(double ticks, int channels) {
        static final Info UNKNOWN = new Info(0, 0);
    }

    private static final Map<ResourceLocation, Info> INFOS = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> PENDING = ConcurrentHashMap.newKeySet();

    /** Forget durations after resources are replaced or a server-synced sound pack changes. */
    public static void clear() {
        INFOS.clear();
        PENDING.clear();
    }

    /** Length of {@code soundId} in ticks, or 0 when unknown / not yet decoded (triggers a load). */
    public static double getTicks(@Nullable ResourceLocation soundId) {
        return info(soundId).ticks();
    }

    /**
     * Channels of the OGG behind {@code soundId} (1 mono, 2 stereo), or 0 when unknown / not yet read (triggers a
     * load). Only a mono sound can be placed in the world: OpenAL plays a stereo one everywhere, unpositioned.
     */
    public static int getChannels(@Nullable ResourceLocation soundId) {
        return info(soundId).channels();
    }

    private static Info info(@Nullable ResourceLocation soundId) {
        if (soundId == null) return Info.UNKNOWN;
        var cached = INFOS.get(soundId);
        if (cached != null) return cached;
        requestLoad(soundId);
        return Info.UNKNOWN;
    }

    private static void requestLoad(ResourceLocation soundId) {
        if (!PENDING.add(soundId)) return; // already loading
        CompletableFuture
                .supplyAsync(() -> computeInfo(soundId), Util.backgroundExecutor())
                .whenComplete((info, error) -> {
                    INFOS.put(soundId, error != null || info == null ? Info.UNKNOWN : info);
                    PENDING.remove(soundId);
                });
    }

    private static Info computeInfo(ResourceLocation soundId) {
        try {
            var mc = Minecraft.getInstance();
            var weighed = mc.getSoundManager().getSoundEvent(soundId);
            if (weighed == null) return Info.UNKNOWN;
            var sound = weighed.getSound(RandomSource.create());
            if (sound == null || sound.getType() != Sound.Type.FILE) return Info.UNKNOWN;
            try (var input = mc.getResourceManager().open(sound.getPath())) {
                var data = input.readAllBytes();
                return new Info(oggDurationTicks(data), AudioConverter.vorbisChannels(data));
            }
        } catch (Exception e) {
            return Info.UNKNOWN;
        }
    }

    /** Reads duration from the Ogg page granules and the Vorbis identification header. */
    static double oggDurationTicks(byte[] data) {
        long lastGranule = -1;
        int sampleRate = 0;
        for (int page = 0; page + 27 <= data.length;) {
            if (data[page] != 'O' || data[page + 1] != 'g' || data[page + 2] != 'g' || data[page + 3] != 'S') {
                page++;
                continue;
            }
            int segments = data[page + 26] & 0xff;
            if (page + 27 + segments > data.length) break;
            int payload = page + 27 + segments;
            int payloadLength = 0;
            for (int i = 0; i < segments; i++) payloadLength += data[page + 27 + i] & 0xff;
            if (payload + payloadLength > data.length) break;
            long granule = 0;
            for (int i = 7; i >= 0; i--) granule = (granule << 8) | (data[page + 6 + i] & 0xffL);
            if (granule >= 0) lastGranule = granule;
            for (int i = payload; sampleRate == 0 && i + 16 <= payload + payloadLength; i++) {
                if (data[i] == 1 && data[i + 1] == 'v' && data[i + 2] == 'o' && data[i + 3] == 'r'
                        && data[i + 4] == 'b' && data[i + 5] == 'i' && data[i + 6] == 's') {
                    sampleRate = (data[i + 12] & 0xff) | ((data[i + 13] & 0xff) << 8)
                            | ((data[i + 14] & 0xff) << 16) | ((data[i + 15] & 0xff) << 24);
                }
            }
            page = payload + payloadLength;
        }
        return sampleRate > 0 && lastGranule > 0 ? lastGranule * 20.0 / sampleRate : 0;
    }
}

