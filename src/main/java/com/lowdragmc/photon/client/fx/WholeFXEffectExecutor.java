package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/** Plays a fixed world-space effect and rotates it as one object around its spawn point. */
@OnlyIn(Dist.CLIENT)
public class WholeFXEffectExecutor extends EntityEffectExecutor implements IWholeEffectTransformer {
    private final Vector3f origin;
    private final Quaternionf baseRotation;

    public WholeFXEffectExecutor(FX fx, Level level, Entity entity, Quaternionf wholeRotation) {
        super(fx, level, entity, AutoRotate.NONE);
        this.origin = entity.position().toVector3f();
        this.baseRotation = new Quaternionf((Quaternionfc) wholeRotation);
    }

    public WholeFXEffectExecutor(FX fx, Level level, Entity entity, Vector3f eulerDegrees, RotationMode mode) {
        this(fx, level, entity, composeEuler(eulerDegrees, mode));
    }

    private static Quaternionf composeEuler(Vector3f degrees, RotationMode mode) {
        float x = (float) Math.toRadians(degrees.x);
        float y = (float) Math.toRadians(degrees.y);
        float z = (float) Math.toRadians(degrees.z);
        return mode == RotationMode.GLOBAL
                ? new Quaternionf().rotateZ(z).rotateY(y).rotateX(x)
                : new Quaternionf().rotateY(y).rotateX(x).rotateZ(z);
    }

    private Quaternionf wholeRotation() { return new Quaternionf((Quaternionfc) baseRotation).mul((Quaternionfc) rotation); }
    private Vector3f pivot() { return new Vector3f((Vector3fc) origin).add((Vector3fc) offset); }

    @Override public Quaternionf applyWholeEffectRotation(Quaternionf particleRotation) {
        return wholeRotation().mul((Quaternionfc) particleRotation);
    }

    @Override public Vector3f applyWholeEffectPosition(Vector3f worldPosition) {
        Vector3f pivot = pivot();
        return wholeRotation().transform(worldPosition.sub((Vector3fc) pivot)).add((Vector3fc) pivot);
    }

    @Override public void updateFXObjectFrame(IFXObject fxObject, float partialTicks) {
        // This executor is intentionally anchored at the original spawn position.
    }

    @Override public void start() {
        if (!entity.isAlive()) return;
        List<EntityEffectExecutor> effects = CACHE.computeIfAbsent(entity, ignored -> new ArrayList<>());
        if (shouldSkipStart(effects)) return;
        resetFinishedNotification();
        runtime = fx.createRuntime();
        IFXObject root = runtime.getRoot();
        root.updatePos(pivot());
        root.updateRotation(wholeRotation());
        root.updateScale(scale);
        runtime.emit(this, delay);
        effects.add(this);
    }

    public enum RotationMode { GLOBAL, LOCAL }
}

