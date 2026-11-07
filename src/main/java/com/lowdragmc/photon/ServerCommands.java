package com.lowdragmc.photon;

import com.lowdragmc.photon.command.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.List;

/**
 * @author KilaBash
 * @date 2023/2/9
 * @implNote ServerCommands
 */
public class ServerCommands {
    public static List<LiteralArgumentBuilder<CommandSourceStack>> createServerCommands() {
        return List.of(
                Commands.literal("photon")
                        // push the current pack list to everybody online: the alternative is asking each player to
                        // reconnect after an upload, which is what makes a server feel like it needs a restart
                        .then(Commands.literal("fxpacks").requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    var players = context.getSource().getServer().getPlayerList().getPlayers();
                                    for (var player : players) {
                                        com.lowdragmc.photon.network.FxPackSync.onRefreshRequest(player);
                                    }
                                    final int count = players.size();
                                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                            "Photon: re-offered FX packs to " + count + " player(s)"), true);
                                    return count;
                                }))
                        .then(Commands.literal("fx").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("location", new FxLocationArgument())
                                        .then(BlockEffectCommand.createServerCommand())
                                        .then(EntityEffectCommand.createServerCommand())
                                )
                                .then(Commands.literal("remove")
                                        .then(RemoveBlockEffectCommand.createServerCommand())
                                        .then(RemoveEntityEffectCommand.createServerCommand())
                                )
                        )

        );
    }
}
