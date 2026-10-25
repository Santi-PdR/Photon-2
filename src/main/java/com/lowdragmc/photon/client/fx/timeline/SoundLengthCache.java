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
 * Best-effort cache of a {@code SoundEvent}'s length in ticks, decoded off-thread from its OGG so an
 * {@link AudioClip} can draw one loop sub-division per repeat. The first lookup for an id kicks off an
 * async decode (via Minecraft's own {@link JOrbisAudioStream}) and returns 0 until it resolves; a
 * sound that can't be measured (missing / event-reference / decode error) caches 0 and is not retried.
 */
@OnlyIn(Dist.CLIENT)
public class SoundLengthCache {
    private static final Map<ResourceLocation, Double> LENGTHS = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> PENDING = ConcurrentHashMap.newKeySet();

    /** Length of {@code soundId} in ticks, or 0 when unknown / not yet decoded (triggers a load). */
    public static double getTicks(@Nullable ResourceLocation soundId) {
        if (soundId == null) return 0;
        var cached = LENGTHS.get(soundId);
        if (cached != null) return cached;
        requestLoad(soundId);
        return 0;
    }

    private static void requestLoad(ResourceLocation soundId) {
        if (!PENDING.add(soundId)) return; // already loading
        CompletableFuture
                .supplyAsync(() -> computeTicks(soundId), Util.backgroundExecutor())
                .whenComplete((ticks, error) -> {
                    LENGTHS.put(soundId, error != null || ticks == null ? 0.0 : ticks);
                    PENDING.remove(soundId);
                });
    }

    private static double computeTicks(ResourceLocation soundId) {
        try {
            var mc = Minecraft.getInstance();
            var weighed = mc.getSoundManager().getSoundEvent(soundId);
            if (weighed == null) return 0;
            var sound = weighed.getSound(RandomSource.create());
            if (sound == null || sound.getType() != Sound.Type.FILE) return 0;
            try (var input = mc.getResourceManager().open(sound.getPath())) {
                return oggDurationTicks(input.readAllBytes());
            }
        } catch (Exception e) {
            return 0;
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

