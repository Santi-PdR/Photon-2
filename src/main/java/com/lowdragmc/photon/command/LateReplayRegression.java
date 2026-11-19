package com.lowdragmc.photon.command;

import com.lowdragmc.photon.Photon;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Late replay is what makes a looping FX visible again to a player who walks away and comes back:
 * the server keeps in-memory snapshots of entity and block FX ({@link ActiveEntityEffects},
 * {@link ActiveWorldEffects}) and re-sends them on StartTracking / ChunkWatch / respawn / dimension
 * change. This regression covers the snapshot core — remember/replace/cap/forget and the replay
 * packet content with its replay flag and per-chunk filtering — without a network connection
 * (the send is a one-liner over {@code pendingReplay}'s output).
 */
public final class LateReplayRegression {
    private LateReplayRegression() {
    }

    public static void verify() throws Exception {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            throw new IllegalStateException("late-replay smoke needs a player");
        }
        var id = Photon.id("late_replay_smoke");
        try {
            // a remembered FX replays with its fields and the replay flag set
            var command = new EntityEffectCommand();
            command.setLocation(id);
            command.setOffset(new Vec3(1, 2, 3));
            command.setRotation(new Vec3(0.1, 0.2, 0.3));
            command.setScale(new Vec3(2, 2, 2));
            command.setDelay(7);
            command.setForcedDeath(false);
            command.setAllowMulti(false);
            ActiveEntityEffects.remember(player, command);
            var packets = ActiveEntityEffects.pendingReplay(player);
            if (packets.size() != 1) {
                throw new IllegalStateException("one remembered FX should replay once, got " + packets.size());
            }
            var replayed = packets.get(0);
            if (!replayed.replay || !id.equals(replayed.location) || replayed.delay != 7
                    || !replayed.offset.equals(new Vec3(1, 2, 3)) || !replayed.scale.equals(new Vec3(2, 2, 2))
                    || !java.util.List.of(player).equals(replayed.entities)) {
                throw new IllegalStateException("the replay packet lost the snapshot's fields or its replay flag");
            }
            // a second play of the same non-multi FX replaces the snapshot instead of stacking
            command.setDelay(9);
            ActiveEntityEffects.remember(player, command);
            packets = ActiveEntityEffects.pendingReplay(player);
            if (packets.size() != 1 || packets.get(0).delay != 9) {
                throw new IllegalStateException("a non-multi FX snapshot was stacked instead of replaced");
            }
            // allowMulti stacks, oldest evicted past 64
            command.setAllowMulti(true);
            for (int i = 0; i < 70; i++) {
                command.setDelay(i);
                ActiveEntityEffects.remember(player, command);
            }
            packets = ActiveEntityEffects.pendingReplay(player);
            if (packets.size() != 64 || packets.get(0).delay != 6 || packets.get(63).delay != 69) {
                throw new IllegalStateException("the per-entity snapshot cap did not evict the oldest entries");
            }
            // forget empties the replay
            ActiveEntityEffects.forget(player, id);
            if (!ActiveEntityEffects.pendingReplay(player).isEmpty()) {
                throw new IllegalStateException("a forgotten FX still replays");
            }

            // block/world side: per-chunk filtering and the replay flag
            var dimension = player.level().dimension().location();
            var block = new BlockEffectCommand();
            block.location = id;
            block.dimension = dimension;
            block.pos = new BlockPos(300, 64, -300);
            block.offset = new Vec3(0, 1, 0);
            block.delay = 4;
            block.allowMulti = false;
            ActiveWorldEffects.remember(block);
            block.delay = 5;
            ActiveWorldEffects.remember(block); // replaces: same location, allowMulti off
            var chunk = new ChunkPos(block.pos);
            var worldPackets = ActiveWorldEffects.pendingReplay(dimension, chunk);
            if (worldPackets.size() != 1 || !worldPackets.get(0).replay || worldPackets.get(0).delay != 5
                    || !block.pos.equals(worldPackets.get(0).pos)) {
                throw new IllegalStateException("the block snapshot did not replay with its fields and replay flag");
            }
            if (!ActiveWorldEffects.pendingReplay(dimension, new ChunkPos(0, 0)).isEmpty()) {
                throw new IllegalStateException("a block FX replayed outside its chunk");
            }
            ResourceLocation otherDimension = dimension.equals(new ResourceLocation("minecraft", "the_nether"))
                    ? new ResourceLocation("minecraft", "overworld") : new ResourceLocation("minecraft", "the_nether");
            if (!ActiveWorldEffects.pendingReplay(otherDimension, chunk).isEmpty()) {
                throw new IllegalStateException("a block FX replayed in another dimension");
            }
            ActiveWorldEffects.forget(dimension, block.pos, id);
            if (!ActiveWorldEffects.pendingReplay(dimension, chunk).isEmpty()) {
                throw new IllegalStateException("a forgotten block FX still replays");
            }
        } finally {
            ActiveEntityEffects.clear();
            ActiveWorldEffects.clear();
        }
        Photon.LOGGER.info("[late-replay-smoke] entity and block FX snapshots replay with their fields and per-chunk filtering");
    }
}
