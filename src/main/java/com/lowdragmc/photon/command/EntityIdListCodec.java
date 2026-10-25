package com.lowdragmc.photon.command;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/** Bounds-checks an entity id list before allocating its backing array. */
final class EntityIdListCodec {
    private EntityIdListCodec() {
    }

    static int[] read(FriendlyByteBuf buf, int trailingBytes) {
        int count = buf.readVarInt();
        int maximumCount = buf.readableBytes() - trailingBytes;
        if (count < 0 || trailingBytes < 0 || count > maximumCount) {
            throw new DecoderException("Invalid entity ID count " + count + " for "
                    + buf.readableBytes() + " remaining packet bytes");
        }
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) ids[i] = buf.readVarInt();
        return ids;
    }
}

