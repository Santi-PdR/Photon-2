package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Math;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Plays an FX attached to an entity: the FX root follows the entity's eye every tick and every
 * frame (plus the configured offset/rotation, optionally auto-rotated to the entity's facing) and
 * the FX is destroyed when the entity dies. All accesses run on the client tick/render thread.
 * <p>
 * Position is applied on the tick as well as the frame. Updating only while the particle renders
 * left the FX frozen at the last drawn pose the moment the player flew or sprinted out of the
 * vanilla particle's stale box. First-person views of the followed entity use the camera itself,
 * because elytra / swim / crawl interpolate eye height on the camera, not on
 * {@link Entity#getEyePosition(float)}.
 */
@OnlyIn(Dist.CLIENT)
public class EntityEffectExecutor extends FXEffectExecutor {
    public enum AutoRotate {
        NONE,
        FORWARD,
        LOOK,
        XROT,
    }
    public static Map<Entity, List<EntityEffectExecutor>> CACHE = new HashMap<>();
    public final Entity entity;
    public final AutoRotate autoRotate;

    public EntityEffectExecutor(FX fx, Level level, Entity entity, AutoRotate autoRotate) {
        super(fx, level);
        this.entity = entity;
        this.autoRotate = autoRotate;
    }

    @Override
    public Entity owner() {
        return entity;
    }

    @Override
    public void updateFXObjectTick(IFXObject fxObject) {
        if (runtime == null || fxObject != runtime.root) {
            return;
        }
        if (!entity.isAlive()) {
            // anchor gone: stop the FX (force drops remnants immediately) and retire right away
            runtime.destroy(forcedDeath);
            retire(CACHE, entity);
        } else if (runtimeEnded()) {
            // self-evict finished runtimes instead of lingering until the next same-key start()
            retire(CACHE, entity);
        } else {
            follow(false, 1f);
        }
    }

    @Override
    public void updateFXObjectFrame(IFXObject fxObject, float partialTicks) {
        if (runtime != null && fxObject == runtime.root) {
            follow(true, partialTicks);
        }
    }

    private void follow(boolean renderFrame, float partialTicks) {
        if (runtime == null || !entity.isAlive()) return;
        var position = followPosition(renderFrame, partialTicks);
        runtime.root.updatePos(new Vector3f(
                (float) (position.x + offset.x),
                (float) (position.y + offset.y),
                (float) (position.z + offset.z)));
        if (autoRotate == AutoRotate.NONE) {
            return;
        }
        switch (autoRotate) {
            case FORWARD -> {
                var forward = entity.getForward();
                var newRotation = new Quaternionf(rotation).rotateXYZ(
                        0,
                        (float) Math.atan2(-forward.z, forward.x),
                        (float) forward.y
                );
                runtime.root.updateRotation(newRotation);
            }
            case LOOK -> {
                var lookAngles = entity.getLookAngle();
                var newRotation = new Quaternionf(rotation).rotateXYZ(
                        0,
                        (float) Math.atan2(-lookAngles.z, lookAngles.x),
                        (float) lookAngles.y
                );
                runtime.root.updateRotation(newRotation);
            }
            case XROT -> {
                var newRotation = new Quaternionf(rotation).rotateXYZ(
                        0,
                        Math.toRadians(-90 - entity.getVisualRotationYInDegrees()),
                        0
                );
                runtime.root.updateRotation(newRotation);
            }
        }
    }

    /**
     * Where the FX should sit this tick/frame. Tick uses the entity's current eye; the render
     * frame interpolates, and in first person of this entity it uses the camera so flight does
     * not leave the FX a frame behind the view.
     */
    private Vec3 followPosition(boolean renderFrame, float partialTicks) {
        if (renderFrame) {
            var renderer = Minecraft.getInstance().gameRenderer;
            if (renderer != null) {
                var camera = renderer.getMainCamera();
                if (camera.getEntity() == entity && !camera.isDetached()) {
                    return camera.getPosition();
                }
            }
            return entity.getEyePosition(partialTicks);
        }
        return entity.getEyePosition();
    }

    @Override
    public void start() {
        if (!entity.isAlive()) {
            FXDiagnostics.failure(fx.getFxLocation(), "start", "Entity is not alive; playback was not started", null, null);
            return;
        }

        var effects = CACHE.computeIfAbsent(entity, p -> new ArrayList<>());
        if (shouldSkipStart(effects)) {
            com.lowdragmc.photon.Photon.LOGGER.info("[fx-diagnostic] fx={} start skipped: equivalent effect already active and allowMulti=false", fx.getFxLocation());
            return;
        }
        resetFinishedNotification();
        this.runtime = createDiagnosedRuntime();
        var root = this.runtime.getRoot();
        root.updatePos(entity.getEyePosition().toVector3f().add(offset.x, offset.y, offset.z));
        root.updateRotation(rotation);
        root.updateScale(scale);
        this.runtime.emit(this, delay, startSection);
        effects.add(this);
    }
}
