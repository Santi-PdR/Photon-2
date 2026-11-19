package com.lowdragmc.photon.command;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/** Bounds-checks an entity id list before allocating its backing array. */
final class EntityIdListCodec {
    static final int MAX_ENTITY_IDS = 4096;

    private EntityIdListCodec() {
    }

    static int[] read(FriendlyByteBuf buf, int trailingBytes) {
        int count = buf.readVarInt();
        int maximumCount = buf.readableBytes() - trailingBytes;
        if (count < 0 || count > MAX_ENTITY_IDS || trailingBytes < 0 || count > maximumCount) {
            throw new DecoderException("Invalid entity ID count " + count + " for "
                    + buf.readableBytes() + " remaining packet bytes");
        }
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) ids[i] = buf.readVarInt();
        return ids;
    }
}

