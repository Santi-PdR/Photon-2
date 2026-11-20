package com.lowdragmc.photon;

import com.lowdragmc.photon.command.BlockEffectCommand;
import com.lowdragmc.photon.command.EditFxCommand;
import com.lowdragmc.photon.command.EffectSectionCommand;
import com.lowdragmc.photon.command.EntityEffectCommand;
import com.lowdragmc.photon.command.RemoveBlockEffectCommand;
import com.lowdragmc.photon.command.RemoveEntityEffectCommand;
import com.lowdragmc.photon.network.FxPackSync;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import net.minecraftforge.network.NetworkDirection;

public class PhotonNetworking {
    private static final String PROTOCOL = "6"; // 6: UploadStatusPacket carries a Component instead of an English string
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Photon.MOD_ID, "main"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, BlockEffectCommand.class, BlockEffectCommand::encode,
                BlockEffectCommand::decodePacket, (packet, context) -> handle(context.get(), () -> BlockEffectCommand.execute(packet)),
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, EntityEffectCommand.class, EntityEffectCommand::encode,
                EntityEffectCommand::decodePacket, (packet, context) -> handle(context.get(), () -> EntityEffectCommand.execute(packet)),
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, RemoveBlockEffectCommand.class, RemoveBlockEffectCommand::encode,
                RemoveBlockEffectCommand::decodePacket, (packet, context) -> handle(context.get(), () -> RemoveBlockEffectCommand.execute(packet)),
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, RemoveEntityEffectCommand.class, RemoveEntityEffectCommand::encode,
                RemoveEntityEffectCommand::decodePacket, (packet, context) -> handle(context.get(), () -> RemoveEntityEffectCommand.execute(packet)),
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(7, EffectSectionCommand.class, EffectSectionCommand::encode,
                EffectSectionCommand::decodePacket, (packet, context) -> handle(context.get(), () -> EffectSectionCommand.execute(packet)),
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(8, EditFxCommand.class, EditFxCommand::encode,
                EditFxCommand::decodePacket, (packet, context) -> handle(context.get(), () -> EditFxCommand.execute(packet)),
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, FxPackSync.ManifestPacket.class, FxPackSync.ManifestPacket::encode,
                FxPackSync.ManifestPacket::decode, (packet, context) -> handle(context.get(),
                        () -> FxPackSync.handleManifest(packet)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5, FxPackSync.RequestPacket.class, FxPackSync.RequestPacket::encode,
                FxPackSync.RequestPacket::decode, (packet, context) -> {
                    var ctx = context.get();
                    handle(ctx, () -> FxPackSync.handleRequest(packet, ctx.getSender()));
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(6, FxPackSync.ChunkPacket.class, FxPackSync.ChunkPacket::encode,
                FxPackSync.ChunkPacket::decode, (packet, context) -> handle(context.get(),
                        () -> FxPackSync.handleChunk(packet)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(9, FxPackSync.UploadPromptPacket.class, FxPackSync.UploadPromptPacket::encode,
                FxPackSync.UploadPromptPacket::decode, (packet, context) -> handle(context.get(),
                        () -> FxPackSync.handleUploadPrompt(packet)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(10, FxPackSync.UploadStartPacket.class, FxPackSync.UploadStartPacket::encode,
                FxPackSync.UploadStartPacket::decode, (packet, context) -> {
                    var ctx = context.get();
                    handle(ctx, () -> FxPackSync.handleUploadStart(packet, ctx.getSender()));
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(11, FxPackSync.UploadChunkPacket.class, FxPackSync.UploadChunkPacket::encode,
                FxPackSync.UploadChunkPacket::decode, (packet, context) -> {
                    var ctx = context.get();
                    handle(ctx, () -> FxPackSync.handleUploadChunk(packet, ctx.getSender()));
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(12, FxPackSync.UploadStatusPacket.class, FxPackSync.UploadStatusPacket::encode,
                FxPackSync.UploadStatusPacket::decode, (packet, context) -> handle(context.get(),
                        () -> FxPackSync.handleUploadStatus(packet)), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static void handle(net.minecraftforge.network.NetworkEvent.Context context, Runnable work) {
        context.enqueueWork(work);
        context.setPacketHandled(true);
    }
}
