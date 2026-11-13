package com.lowdragmc.photon.command;

import com.lowdragmc.photon.PhotonNetworking;
import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side copy of entity FX that are still supposed to be playing. The play packet only
 * reaches players who currently track the entity, so someone who walks away (or is teleported)
 * and comes back would otherwise never see it again — while the owner, who never unloaded
 * themselves, still does.
 */
public final class ActiveEntityEffects {
    private static final Map<UUID, List<Snapshot>> ACTIVE = new HashMap<>();

    private ActiveEntityEffects() {
    }

    static void remember(Entity entity, EntityEffectCommand command) {
        if (entity == null || command.location == null) return;
        var snapshot = Snapshot.from(command);
        var list = ACTIVE.computeIfAbsent(entity.getUUID(), id -> new ArrayList<>());
        if (!snapshot.allowMulti) {
            list.removeIf(existing -> existing.location.equals(snapshot.location));
        }
        list.add(snapshot);
    }

    public static void forget(Entity entity, @Nullable ResourceLocation location) {
        if (entity != null) forget(entity.getUUID(), location);
    }

    static void forget(UUID id, @Nullable ResourceLocation location) {
        if (id == null) return;
        if (location == null) {
            ACTIVE.remove(id);
            return;
        }
        var list = ACTIVE.get(id);
        if (list == null) return;
        list.removeIf(snapshot -> location.equals(snapshot.location));
        if (list.isEmpty()) ACTIVE.remove(id);
    }

    /** Re-send every stored FX on {@code target} to {@code player} (entity is present on their client). */
    public static void replayTo(ServerPlayer player, Entity target) {
        if (player == null || target == null) return;
        var list = ACTIVE.get(target.getUUID());
        if (list == null || list.isEmpty()) return;
        for (var snapshot : list) {
            PhotonNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot.packet(target));
        }
    }

    private record Snapshot(ResourceLocation location, Vec3 offset, Vec3 rotation, Vec3 scale,
                            int delay, boolean forcedDeath, boolean allowMulti,
                            EntityEffectExecutor.AutoRotate autoRotate) {
        static Snapshot from(EntityEffectCommand command) {
            return new Snapshot(command.location, command.offset, command.rotation, command.scale,
                    command.delay, command.forcedDeath, command.allowMulti, command.autoRotate());
        }

        EntityEffectCommand packet(Entity entity) {
            var command = new EntityEffectCommand();
            command.setLocation(location);
            command.setOffset(offset);
            command.setRotation(rotation);
            command.setScale(scale);
            command.setDelay(delay);
            command.setForcedDeath(forcedDeath);
            command.setAllowMulti(allowMulti);
            command.setAutoRotate(autoRotate);
            command.setEntities(List.of(entity));
            return command;
        }
    }
}
