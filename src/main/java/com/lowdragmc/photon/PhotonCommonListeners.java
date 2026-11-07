package com.lowdragmc.photon;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

@EventBusSubscriber(modid = Photon.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public class PhotonCommonListeners {
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.lowdragmc.photon.network.FxPackSync.onPlayerLogin(player);
        }
    }

    /**
     * Login is not the only moment a player can learn about a new pack: a dimension change or a respawn reloads
     * nothing on the client, but it is a fresh level, and it is the cheapest place to re-offer the list.
     */
    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.lowdragmc.photon.network.FxPackSync.onPlayerJoinLevel(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.lowdragmc.photon.network.FxPackSync.onPlayerJoinLevel(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.lowdragmc.photon.network.FxPackSync.onPlayerLogout(player);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        List<LiteralArgumentBuilder<CommandSourceStack>> commands = ServerCommands.createServerCommands();
        commands.forEach(dispatcher::register);
    }
}
