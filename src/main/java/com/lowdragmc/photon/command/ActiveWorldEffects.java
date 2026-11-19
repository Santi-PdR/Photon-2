package com.lowdragmc.photon.command;

import com.lowdragmc.photon.PhotonNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory snapshots of block/world FX, replayed as chunks become visible to a player. */
public final class ActiveWorldEffects {
    private static final int MAX_POSITIONS = 4096;
    private static final int MAX_EFFECTS_PER_POSITION = 64;
    private static final Map<WorldPosition, List<Snapshot>> ACTIVE = new LinkedHashMap<>();

    private ActiveWorldEffects() {
    }

    static void remember(BlockEffectCommand command) {
        if (command.pos == null || command.dimension == null || command.location == null) return;
        var key = new WorldPosition(command.dimension, command.pos.immutable());
        var list = ACTIVE.get(key);
        if (list == null) {
            if (ACTIVE.size() >= MAX_POSITIONS) ACTIVE.remove(ACTIVE.keySet().iterator().next());
            list = new ArrayList<>();
            ACTIVE.put(key, list);
        }
        var snapshot = Snapshot.from(command);
        if (!snapshot.allowMulti) list.removeIf(existing -> existing.location.equals(snapshot.location));
        list.add(snapshot);
        while (list.size() > MAX_EFFECTS_PER_POSITION) list.remove(0);
    }

    static void forget(ResourceLocation dimension, BlockPos pos, @Nullable ResourceLocation location) {
        var key = new WorldPosition(dimension, pos.immutable());
        var list = ACTIVE.get(key);
        if (list == null) return;
        if (location == null) {
            ACTIVE.remove(key);
            return;
        }
        list.removeIf(snapshot -> location.equals(snapshot.location));
        if (list.isEmpty()) ACTIVE.remove(key);
    }

    public static void replayTo(ServerPlayer player, ServerLevel level, ChunkPos chunkPos) {
        if (player == null || level == null || !player.serverLevel().dimension().equals(level.dimension())) return;
        var chunkX = chunkPos.x;
        var chunkZ = chunkPos.z;
        for (var entry : ACTIVE.entrySet()) {
            var key = entry.getKey();
            if (!key.dimension.equals(level.dimension().location())
                    || (key.pos.getX() >> 4) != chunkX || (key.pos.getZ() >> 4) != chunkZ) continue;
            for (var snapshot : List.copyOf(entry.getValue())) {
                PhotonNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot.packet(key));
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private record WorldPosition(ResourceLocation dimension, BlockPos pos) {
    }

    private record Snapshot(ResourceLocation location, net.minecraft.world.phys.Vec3 offset,
                            net.minecraft.world.phys.Vec3 rotation, net.minecraft.world.phys.Vec3 scale,
                            int delay, boolean forcedDeath, boolean allowMulti, boolean checkState) {
        static Snapshot from(BlockEffectCommand command) {
            return new Snapshot(command.location, command.offset, command.rotation, command.scale,
                    command.delay, command.forcedDeath, command.allowMulti, command.checkState);
        }

        BlockEffectCommand packet(WorldPosition key) {
            var command = new BlockEffectCommand();
            command.location = location;
            command.dimension = key.dimension;
            command.pos = key.pos;
            command.offset = offset;
            command.rotation = rotation;
            command.scale = scale;
            command.delay = delay;
            command.forcedDeath = forcedDeath;
            command.allowMulti = allowMulti;
            command.checkState = checkState;
            command.replay = true;
            return command;
        }
    }
}
