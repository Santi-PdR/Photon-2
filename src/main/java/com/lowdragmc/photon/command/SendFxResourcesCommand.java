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
                                context.getSource().sendFailure(Component.translatable("photon.command.send.in_game_only"));
                                return 0;
                            }
                            var recipient = EntityArgument.getPlayer(context, "player");
                            if (!FxPackSync.beginLocalResourceUpload(sender, recipient)) {
                                context.getSource().sendFailure(Component.translatable("photon.command.send.could_not_start"));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.translatable("photon.command.send.asking_client",
                                    recipient.getGameProfile().getName()), false);
                            return 1;
                        }));
    }
}
