package com.lowdragmc.photon.command;

import lombok.Setter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * @author KilaBash
 * @date 2023/6/12
 * @implNote EffectCommand
 */
public abstract class EffectCommand {
    private static final double MAX_PACKET_OFFSET = 1_000_000.0;
    private static final double MAX_PACKET_ROTATION = 1_000_000.0;
    private static final double MAX_PACKET_SCALE = 10_000.0;

    @Setter
    protected ResourceLocation location;
    @Setter
    protected Vec3 offset = Vec3.ZERO;
    @Setter
    protected Vec3 rotation = Vec3.ZERO;
    @Setter
    protected Vec3 scale = new Vec3(1, 1, 1);
    @Setter
    protected int delay;
    @Setter
    protected boolean forcedDeath;
    @Setter
    protected boolean allowMulti;

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(location);
        buf.writeDouble(offset.x);
        buf.writeDouble(offset.y);
        buf.writeDouble(offset.z);
        buf.writeDouble(rotation.x);
        buf.writeDouble(rotation.y);
        buf.writeDouble(rotation.z);
        buf.writeDouble(scale.x);
        buf.writeDouble(scale.y);
        buf.writeDouble(scale.z);
        buf.writeVarInt(delay);
        buf.writeBoolean(forcedDeath);
        buf.writeBoolean(allowMulti);
    }

    public void decode(FriendlyByteBuf buf) {
        location = buf.readResourceLocation();
        offset = readBoundedVector(buf, Vec3.ZERO, MAX_PACKET_OFFSET);
        rotation = readBoundedVector(buf, Vec3.ZERO, MAX_PACKET_ROTATION);
        scale = readBoundedVector(buf, new Vec3(1, 1, 1), MAX_PACKET_SCALE);
        delay = Math.max(0, buf.readVarInt());
        forcedDeath = buf.readBoolean();
        allowMulti = buf.readBoolean();
    }

    private static Vec3 readBoundedVector(FriendlyByteBuf buf, Vec3 fallback, double limit) {
        return new Vec3(readBounded(buf.readDouble(), fallback.x, limit),
                readBounded(buf.readDouble(), fallback.y, limit),
                readBounded(buf.readDouble(), fallback.z, limit));
    }

    private static double readBounded(double value, double fallback, double limit) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(-limit, Math.min(limit, value));
    }

}
