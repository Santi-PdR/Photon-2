package com.lowdragmc.photon;

import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServerCommandsTest {
    @Test
    void serverCommandClassLoadsAndBuildsThePhotonCommandTree() {
        var commands = ServerCommands.createServerCommands();

        assertEquals(1, commands.size());
        LiteralCommandNode<CommandSourceStack> photon = commands.get(0).build();
        assertEquals("photon", photon.getName());

        var fx = photon.getChild("fx");
        assertNotNull(fx, "/photon fx is registered");
        var location = fx.getChild("location");
        assertNotNull(location, "/photon fx <location> is registered");
        assertNotNull(location.getChild("block"), "/photon fx <location> block is registered");
        assertNotNull(location.getChild("entity"), "/photon fx <location> entity is registered");

        var remove = fx.getChild("remove");
        assertNotNull(remove, "/photon fx remove is registered");
        assertNotNull(remove.getChild("block"), "/photon fx remove block is registered");
        assertNotNull(remove.getChild("entity"), "/photon fx remove entity is registered");
    }
}
