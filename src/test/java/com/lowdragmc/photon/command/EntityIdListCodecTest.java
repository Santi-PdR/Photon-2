package com.lowdragmc.photon.command;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityIdListCodecTest {
    @Test
    void readsEntityIdsAndLeavesTheDeclaredTrailingFieldsUntouched() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(3);
            buf.writeVarInt(7);
            buf.writeVarInt(300);
            buf.writeVarInt(999_999);
            buf.writeBoolean(true);
            buf.writeBoolean(false);

            assertArrayEquals(new int[]{7, 300, 999_999}, EntityIdListCodec.read(buf, 2));
            assertTrue(buf.readBoolean());
            assertFalse(buf.readBoolean());
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    @Test
    void rejectsNegativeEntityCountBeforeAllocating() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(-1);
            buf.writeBoolean(false);
            buf.writeBoolean(false);

            assertThrows(DecoderException.class, () -> EntityIdListCodec.read(buf, 2));
        } finally {
            buf.release();
        }
    }

    @Test
    void rejectsCountsLargerThanThePacketCanContain() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(1_000_000);
            buf.writeVarInt(42);
            buf.writeBoolean(false);
            buf.writeBoolean(false);

            assertThrows(DecoderException.class, () -> EntityIdListCodec.read(buf, 2));
        } finally {
            buf.release();
        }
    }
}
