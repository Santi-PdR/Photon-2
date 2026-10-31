package com.lowdragmc.photon.command;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonNetworking;
import com.lowdragmc.photon.client.fx.BlockEffectExecutor;
import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Setter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class RemoveEntityEffectCommand {
    public static final ResourceLocation ID = Photon.id("remove_entity_effect_command");

    @Setter
    protected List<Entity> entities;
    // client
    private int[] ids = new int[0];
    @Setter
    protected boolean force;
    @Nullable
    @Setter
    protected ResourceLocation location;

    public static LiteralArgumentBuilder<CommandSourceStack> createServerCommand() {
        return Commands.literal("entity")
                .then(Commands.argument("entities", EntityArgument.entities())
                        .executes(c -> execute(c, false, false))
                        .then(Commands.argument("force", BoolArgumentType.bool())
                                .executes(c -> execute(c, true, false))
                                .then(Commands.argument("location", ResourceLocationArgument.id())
                                        .executes(c -> execute(c, true, true)))));
    }

    private static int execute(CommandContext<CommandSourceStack> context, boolean force, boolean location) throws CommandSyntaxException {
        var command = new RemoveEntityEffectCommand();
        command.setEntities(EntityArgument.getEntities(context, "entities").stream().map(e -> (Entity) e).toList());
        if (force) {
            command.setForce(BoolArgumentType.getBool(context, "force"));
        }
        if (location) {
            command.setLocation(ResourceLocationArgument.getId(context, "location"));
        }
        PhotonNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(), command);
        return Command.SINGLE_SUCCESS;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entities.size());
        for (Entity entity : entities) {
            buf.writeVarInt(entity.getId());
        }
        buf.writeBoolean(force);
        buf.writeBoolean(location != null);
        if (location != null) {
            buf.writeResourceLocation(location);
        }
    }

    public void decode(FriendlyByteBuf buf) {
        ids = EntityIdListCodec.read(buf, 2);
        force = buf.readBoolean();
        if (buf.readBoolean()) {
            location = buf.readResourceLocation();
        }
    }

    public static RemoveEntityEffectCommand decodePacket(FriendlyByteBuf buf) {
        var packet = new RemoveEntityEffectCommand();
        packet.decode(buf);
        return packet;
    }

    public static void execute(RemoveEntityEffectCommand packet) {
        if (LDLib2.isClient()) {
            Client.execute(packet);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static class Client {
        public static void execute(RemoveEntityEffectCommand packet) {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            var targets = new ArrayList<Entity>(packet.ids.length);
            for (var id : packet.ids) {
                var entity = level.getEntity(id);
                if (entity != null) targets.add(entity);
            }
            EffectRemoval.removeMatching(targets, EntityEffectExecutor.CACHE,
                    effect -> packet.location == null || packet.location.equals(effect.getFx().getFxLocation()),
                    effect -> {
                        var runtime = effect.getRuntime();
                        if (runtime != null && runtime.isAlive()) runtime.destroy(packet.force);
                    });
        }
    }

}

