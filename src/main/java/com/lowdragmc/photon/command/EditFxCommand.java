package com.lowdragmc.photon.command;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonNetworking;
import com.lowdragmc.photon.gui.editor.FXFileActions;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.PacketDistributor;

/**
 * {@code /photon fx editfx <location>}: open that effect in the FX editor. Saving the project writes
 * the playable {@code .fx} over the Photon workspace copy of the same id, so the next play (or a
 * resource reload) replaces what is currently loaded.
 */
public class EditFxCommand {
    public static final ResourceLocation ID = Photon.id("edit_fx_command");

    private ResourceLocation location;

    public EditFxCommand() {
    }

    public EditFxCommand(ResourceLocation location) {
        this.location = location;
    }

    public ResourceLocation location() {
        return location;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createServerCommand() {
        return Commands.literal("editfx")
                .then(Commands.argument("location", new FxLocationArgument())
                        .executes(EditFxCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var command = new EditFxCommand();
        command.location = ResourceLocationArgument.getId(context, "location");
        PhotonNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), command);
        context.getSource().sendSuccess(() -> Component.translatable("photon.editfx.opened", command.location.toString()), true);
        return Command.SINGLE_SUCCESS;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(location);
    }

    public void decode(FriendlyByteBuf buf) {
        location = buf.readResourceLocation();
    }

    public static EditFxCommand decodePacket(FriendlyByteBuf buf) {
        var packet = new EditFxCommand();
        packet.decode(buf);
        return packet;
    }

    public static void execute(EditFxCommand packet) {
        if (LDLib2.isClient()) {
            Client.execute(packet);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static final class Client {
        private Client() {
        }

        static void execute(EditFxCommand packet) {
            var player = Minecraft.getInstance().player;
            try {
                if (!FXFileActions.openForEdit(packet.location)) {
                    if (player != null) {
                        player.sendSystemMessage(Component.translatable("photon.editfx.missing", packet.location.toString()));
                    }
                }
            } catch (Exception e) {
                Photon.LOGGER.error("Failed to open {} in the FX editor", packet.location, e);
                if (player != null) {
                    player.sendSystemMessage(Component.translatable("photon.editfx.failed",
                            packet.location.toString(), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                }
            }
        }
    }
}
