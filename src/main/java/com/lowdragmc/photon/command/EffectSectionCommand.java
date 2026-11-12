package com.lowdragmc.photon.command;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.PhotonNetworking;
import com.lowdragmc.photon.client.fx.BlockEffectExecutor;
import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import com.lowdragmc.photon.client.fx.FXEffectExecutor;
import com.lowdragmc.photon.client.fx.timeline.SectionControl;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /photon fx section (entity <entities> | block <pos>) (jump <section> | queue <section> | advance | release)
 * [location]}: steers the timeline of an effect that is already playing on entities or on a block, moving it between
 * the named sections its timeline was split into. {@code location} narrows it to the effects of one .fx id.
 * <p>
 * Ported from Photon 26.3/2.2.11. The four actions are exactly the ones {@link SectionControl} exposes, and each one
 * returns whether it did anything: a jump to a section the effect does not have, or a request on an effect that has
 * already ended, changes nothing.
 */
public class EffectSectionCommand {
    public static final ResourceLocation ID = Photon.id("effect_section_command");

    public enum Action {
        /** From the next tick on, play from the start of a named section. */
        JUMP,
        /** Finish the current pass, then go to a named section without a break. */
        QUEUE,
        /** Stop repeating: when this pass ends, follow the section's "then". */
        ADVANCE,
        /** Play the release section: the effect says goodbye instead of stopping dead. */
        RELEASE;

        boolean named() {
            return this == JUMP || this == QUEUE;
        }
    }

    @Nullable
    private BlockPos pos;
    private int[] entities = new int[0];
    private Action action = Action.JUMP;
    private String section = "";
    @Nullable
    private ResourceLocation location;

    public EffectSectionCommand() {
    }

    /** Construct the named action payload used by clients and tests. */
    public EffectSectionCommand(Action action, String section) {
        this.action = action;
        this.section = section == null ? "" : section;
    }

    public Action action() {
        return action;
    }

    public String section() {
        return section;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createServerCommand() {
        return Commands.literal("section")
                .then(Commands.literal("entity").then(actions(Commands.argument("entities", EntityArgument.entities()), false)))
                .then(Commands.literal("block").then(actions(Commands.argument("pos", BlockPosArgument.blockPos()), true)));
    }

    /** The four actions, each with an optional trailing {@code location} argument. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T actions(T target, boolean block) {
        for (var action : Action.values()) {
            var literal = Commands.literal(action.name().toLowerCase(Locale.ROOT));
            var withLocation = Commands.argument("location", ResourceLocationArgument.id())
                    .executes(c -> execute(c, block, action, true));
            if (action.named()) {
                literal.then(Commands.argument("section", StringArgumentType.string())
                        .executes(c -> execute(c, block, action, false))
                        .then(withLocation));
            } else {
                literal.executes(c -> execute(c, block, action, false)).then(withLocation);
            }
            target.then(literal);
        }
        return target;
    }

    private static int execute(CommandContext<CommandSourceStack> context, boolean block, Action action, boolean location)
            throws CommandSyntaxException {
        var command = new EffectSectionCommand();
        command.action = action;
        if (action.named()) {
            command.section = StringArgumentType.getString(context, "section");
        }
        if (location) {
            command.location = ResourceLocationArgument.getId(context, "location");
        }
        if (block) {
            command.pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
            // only the clients that can see the block: the effects live in their world
            PhotonNetworking.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() ->
                    context.getSource().getLevel().getChunkAt(command.pos)), command);
        } else {
            var ids = new ArrayList<Integer>();
            for (Entity entity : EntityArgument.getEntities(context, "entities")) {
                ids.add(entity.getId());
            }
            command.entities = ids.stream().mapToInt(Integer::intValue).toArray();
            PhotonNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(), command);
        }
        return Command.SINGLE_SUCCESS;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(pos != null);
        buf.writeVarIntArray(pos != null ? new int[0] : entities);
        if (pos != null) {
            buf.writeBlockPos(pos);
        }
        buf.writeEnum(action);
        buf.writeUtf(section);
        buf.writeBoolean(location != null);
        if (location != null) {
            buf.writeResourceLocation(location);
        }
    }

    public static EffectSectionCommand decodePacket(FriendlyByteBuf buf) {
        var packet = new EffectSectionCommand();
        var hasPos = buf.readBoolean();
        var ids = buf.readVarIntArray();
        if (hasPos) {
            packet.pos = buf.readBlockPos();
        } else {
            packet.entities = ids;
        }
        packet.action = buf.readEnum(Action.class);
        packet.section = buf.readUtf();
        if (buf.readBoolean()) {
            packet.location = buf.readResourceLocation();
        }
        return packet;
    }

    public static void execute(EffectSectionCommand packet) {
        if (LDLib2.isClient()) {
            Client.execute(packet);
        }
    }

    /** Ask one effect's timeline to do this; false when the request changed nothing. */
    public boolean apply(SectionControl sections) {
        return switch (action) {
            case JUMP -> sections.jumpTo(section);
            case QUEUE -> sections.queue(section);
            case ADVANCE -> sections.advance();
            case RELEASE -> sections.release();
        };
    }

    @OnlyIn(Dist.CLIENT)
    private static class Client {
        public static void execute(EffectSectionCommand packet) {
            if (packet.pos != null) {
                apply(packet, BlockEffectExecutor.CACHE.get(packet.pos));
                return;
            }
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            for (var id : packet.entities) {
                var entity = level.getEntity(id);
                if (entity != null) {
                    apply(packet, EntityEffectExecutor.CACHE.get(entity));
                }
            }
        }

        private static void apply(EffectSectionCommand packet, @Nullable List<? extends FXEffectExecutor> effects) {
            if (effects == null) return;
            // a copy: an effect that finishes while this runs retires itself out of the cache list
            for (var effect : List.copyOf(effects)) {
                var runtime = effect.getRuntime();
                if (runtime != null && (packet.location == null
                        || packet.location.equals(effect.getFx().getFxLocation()))) {
                    packet.apply(runtime.sections());
                }
            }
        }
    }
}
