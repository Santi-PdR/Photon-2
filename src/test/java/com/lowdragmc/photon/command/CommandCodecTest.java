package com.lowdragmc.photon.command;

import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CommandCodecTest {
    private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath("photon", "test_effect");

    @Test
    void blockEffectCodecRoundTripsEveryField() {
        var command = new BlockEffectCommand();
        command.setLocation(EFFECT);
        command.setOffset(new Vec3(1.25, -2.5, 3.75));
        command.setRotation(new Vec3(4, 5, 6));
        command.setScale(new Vec3(0.5, 1.5, 2.5));
        command.setDelay(127);
        command.setForcedDeath(true);
        command.setAllowMulti(true);
        command.setPos(new BlockPos(-17, 64, 301));
        command.setCheckState(true);

        assertRoundTrip(command, BlockEffectCommand::encode, BlockEffectCommand::decodePacket);
    }

    @Test
    void entityEffectCodecRoundTripsBaseFieldsAndEnum() {
        var command = new EntityEffectCommand();
        command.setLocation(EFFECT);
        command.setOffset(new Vec3(1, 2, 3));
        command.setRotation(new Vec3(4, 5, 6));
        command.setScale(new Vec3(0.25, 0.5, 0.75));
        command.setDelay(300);
        command.setForcedDeath(true);
        command.setAllowMulti(false);
        command.setEntities(List.of());
        command.setAutoRotate(EntityEffectExecutor.AutoRotate.XROT);

        assertRoundTrip(command, EntityEffectCommand::encode, EntityEffectCommand::decodePacket,
                decoded -> decoded.setEntities(List.of()));
    }

    @Test
    void removeBlockCodecRoundTripsWithAndWithoutLocation() {
        var withoutLocation = new RemoveBlockEffectCommand();
        withoutLocation.pos = new BlockPos(3, -4, 5);
        withoutLocation.setForce(true);
        assertRoundTrip(withoutLocation, RemoveBlockEffectCommand::encode, RemoveBlockEffectCommand::decodePacket);

        var withLocation = new RemoveBlockEffectCommand();
        withLocation.pos = new BlockPos(-30, 255, 80);
        withLocation.setForce(false);
        withLocation.setLocation(EFFECT);
        assertRoundTrip(withLocation, RemoveBlockEffectCommand::encode, RemoveBlockEffectCommand::decodePacket);
    }

    @Test
    void removeEntityCodecRoundTripsWithAndWithoutLocation() {
        var withoutLocation = new RemoveEntityEffectCommand();
        withoutLocation.setEntities(List.of());
        withoutLocation.setForce(true);
        assertRoundTrip(withoutLocation, RemoveEntityEffectCommand::encode, RemoveEntityEffectCommand::decodePacket,
                decoded -> decoded.setEntities(List.of()));

        var withLocation = new RemoveEntityEffectCommand();
        withLocation.setEntities(List.of());
        withLocation.setForce(false);
        withLocation.setLocation(EFFECT);
        assertRoundTrip(withLocation, RemoveEntityEffectCommand::encode, RemoveEntityEffectCommand::decodePacket,
                decoded -> decoded.setEntities(List.of()));
    }

    private static <T> void assertRoundTrip(T command, Encoder<T> encoder, Decoder<T> decoder,
                                           java.util.function.Consumer<T> afterDecode) {
        var written = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf payload = null;
        var rewritten = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encoder.encode(command, written);
            var expected = new byte[written.readableBytes()];
            written.getBytes(written.readerIndex(), expected);

            payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(expected));
            var packet = decoder.decode(payload);
            assertFalse(payload.isReadable(), "decoder must consume the complete payload");
            afterDecode.accept(packet);
            encoder.encode(packet, rewritten);
            var actual = new byte[rewritten.readableBytes()];
            rewritten.getBytes(rewritten.readerIndex(), actual);
            assertArrayEquals(expected, actual);
        } finally {
            written.release();
            if (payload != null) payload.release();
            rewritten.release();
        }
    }

    private static <T> void assertRoundTrip(T command, Encoder<T> encoder, Decoder<T> decoder) {
        assertRoundTrip(command, encoder, decoder, ignored -> { });
    }

    @FunctionalInterface
    private interface Encoder<T> {
        void encode(T packet, FriendlyByteBuf buf);
    }

    @FunctionalInterface
    private interface Decoder<T> {
        T decode(FriendlyByteBuf buf);
    }
}
