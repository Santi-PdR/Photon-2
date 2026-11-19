package com.lowdragmc.photon.command;

import com.lowdragmc.photon.network.FxPackSync;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Starts an operator-authorized transfer of the local LDLib2 assets folder to one online player. */
public final class SendFxResourcesCommand {
    private SendFxResourcesCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("send")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> {
                            if (!(context.getSource().getEntity() instanceof ServerPlayer sender)) {
                                context.getSource().sendFailure(Component.literal("Photon: run this command in-game."));
                                return 0;
                            }
                            var recipient = EntityArgument.getPlayer(context, "player");
                            if (!FxPackSync.beginLocalResourceUpload(sender, recipient)) {
                                context.getSource().sendFailure(Component.literal("Photon: could not start the resource transfer."));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.literal("Photon: asking your client to package ldlib2/assets for "
                                    + recipient.getGameProfile().getName() + "…"), false);
                            return 1;
                        }));
    }
}
