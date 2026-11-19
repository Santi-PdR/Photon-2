package com.lowdragmc.photon.client.gameobject.emitter.data.material.kila;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.PhotonGpuChannels;

/**
 * A number a material can take from each particle: {@code value + scale * source}. A source the draw cannot
 * read (no instancing, no such stream) reads 0, so it falls back to {@code value}.
 */
public class KilaDriver implements IPersistedSerializable {
    /** ⚠️ codes MIRRORED IN {@code photon:kila.vsh} {@code kila_source}. */
    public enum Source {
        CONSTANT(0),
        VERTEX_R(1),
        VERTEX_G(2),
        VERTEX_B(3),
        VERTEX_A(4),
        LIFE(5),
        RANDOM(6),
        POINT_LIFE(7),
        POINT_T(8),
        CUSTOM(16);

        public final int code;

        Source(int code) {
            this.code = code;
        }

        public String langKey() {
            return "kila.source." + name().toLowerCase(java.util.Locale.ROOT);
        }

        /** Whether the value comes from per-particle data that only some draws carry. */
        public boolean isParticleData() {
            return code >= LIFE.code;
        }
    }

    @Persisted
    public float value;
    @Persisted
    public Source source = Source.CONSTANT;
    @Persisted
    public float scale = 1;
    @Persisted
    public int stream;
    @Persisted
    public int channel;

    public KilaDriver() {
    }

    public KilaDriver(float value) {
        this.value = value;
    }

    public KilaDriver bind(Source source, float scale) {
        this.source = source;
        this.scale = scale;
        return this;
    }

    public KilaDriver bindCustom(int stream, int channel, float scale) {
        this.source = Source.CUSTOM;
        this.stream = stream;
        this.channel = channel;
        this.scale = scale;
        return this;
    }

    /** A corrupt or old enum value must behave like the harmless constant default. */
    public Source effectiveSource() {
        return source == null ? Source.CONSTANT : source;
    }

    private static float finiteBounded(float value, float fallback) {
        if (!Float.isFinite(value)) return fallback;
        return Math.max(-1_000_000f, Math.min(1_000_000f, value));
    }

    public float safeValue() {
        return finiteBounded(value, 0f);
    }

    public float safeScale() {
        return finiteBounded(scale, 0f);
    }

    public boolean isDriven() {
        return effectiveSource() != Source.CONSTANT;
    }

    /** The code {@code kila_source} switches on. */
    public int code() {
        var effective = effectiveSource();
        if (effective != Source.CUSTOM) return effective.code;
        return Source.CUSTOM.code + net.minecraft.util.Mth.clamp(stream, 0, 3) * 4 + net.minecraft.util.Mth.clamp(channel, 0, 3);
    }

    /** {@code PhotonGpuChannels} bits this driver reads. */
    public long channelMask() {
        var id = switch (effectiveSource()) {
            case LIFE -> "addition_gpu_data.t";
            case RANDOM -> "addition_gpu_data.random";
            case POINT_LIFE -> "addition_gpu_data.point_life";
            case POINT_T -> "addition_gpu_data.point_t";
            default -> null;
        };
        if (id == null) return 0L;
        var channel = PhotonGpuChannels.byId(id);
        return channel == null ? 0L : channel.bit();
    }

    public boolean readsCustomData() {
        return effectiveSource() == Source.CUSTOM;
    }

    public boolean readsParticleData() {
        return effectiveSource().isParticleData();
    }

    public void copyFrom(KilaDriver other) {
        value = other.value;
        source = other.source;
        scale = other.scale;
        stream = other.stream;
        channel = other.channel;
    }
}

