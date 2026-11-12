package com.lowdragmc.photon.client.gameobject;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.ISceneObject;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.fx.PreloadContext;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import com.mojang.serialization.Codec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * FXObject is a scene object that is used for FXRuntime.
 * <br>
 * e.g. {@link Emitter}
 */
public interface IFXObject extends ISceneObject, IPersistedSerializable, IConfigurable {
    // dispatch on the object's FXObjectType; the discriminator is the type's registry name, and each
    // type owns its own (versioned) serialization via FXObjectType#codec().
    Codec<IFXObject> CODEC = PhotonRegistries.FX_OBJECTS.optionalCodec().dispatch(
            obj -> Optional.ofNullable(obj.getFXObjectType()),
            optional -> optional.map(type -> type.codec().fieldOf("data").codec())
                    .orElseGet(() -> LDLibExtraCodecs.<IFXObject>errorDecoder().codec()));


    @Nullable
    default CompoundTag serializeWrapper() {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).result().orElse(null);
    }

    @Nullable
    static IFXObject deserializeWrapper(Tag tag) {
        var result = CODEC.parse(NbtOps.INSTANCE, fixLegacyBlendNames(fixOldIdentityRotation(
                com.lowdragmc.photon.client.fx.compat.MapperUtils.repairLegacyCodecs(tag))));
        result.error().ifPresent(error -> com.lowdragmc.photon.Photon.LOGGER.warn(
                "[fx-diagnostic] could not decode FX object type={}: {}",
                tag instanceof CompoundTag compound ? compound.getString("type") : "unknown", error.message()));
        return result.result().orElse(null);
    }

    /** Previously converted objects may still contain Photon 1 blend names. Repair a copy at load,
     * including nested trail/material data; never modify the source file. */
    private static Tag fixLegacyBlendNames(Tag tag) {
        var copy = tag.copy();
        return normalizeLegacyBlendNames(copy) ? copy : tag;
    }

    private static boolean normalizeLegacyBlendNames(Tag tag) {
        boolean changed = false;
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("blendMode", Tag.TAG_COMPOUND)) {
                var blend = compound.getCompound("blendMode");
                switch (blend.getString("blendFunc")) {
                    case "sub" -> { blend.putString("blendFunc", "SUB"); changed = true; }
                    case "REVERSE_sub" -> { blend.putString("blendFunc", "REVERSE_SUB"); changed = true; }
                }
            }
            for (var key : compound.getAllKeys()) changed |= normalizeLegacyBlendNames(compound.get(key));
        } else if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (var entry : list) changed |= normalizeLegacyBlendNames(entry);
        }
        return changed;
    }

    /**
     * An earlier Photon 1 converter saved an absent rotation as [0,0,0] rather than the four-component
     * identity quaternion. Those already-converted files are marked current, so the versioned datafixer
     * cannot repair them. Fix only that exact identity shape on a copy, at the common object decoder used
     * by effects, nested effects and projects; never rewrite the author's file or guess nonzero rotations.
     */
    private static Tag fixOldIdentityRotation(Tag tag) {
        if (!(tag instanceof CompoundTag wrapper) || !wrapper.contains("data", Tag.TAG_COMPOUND)) return tag;
        var data = wrapper.getCompound("data");
        if (!data.contains("transform", Tag.TAG_COMPOUND)) return tag;
        var transform = data.getCompound("transform");
        if (!transform.contains("localRotation", Tag.TAG_LIST)) return tag;
        var rotation = transform.getList("localRotation", Tag.TAG_FLOAT);
        if (rotation.size() != 3 || rotation.getFloat(0) != 0f || rotation.getFloat(1) != 0f
                || rotation.getFloat(2) != 0f) return tag;
        var fixed = wrapper.copy();
        var quaternion = rotation.copy();
        quaternion.add(net.minecraft.nbt.FloatTag.valueOf(1f));
        fixed.getCompound("data").getCompound("transform").put("localRotation", quaternion);
        return fixed;
    }

    default IGuiTexture getIcon() {
        return IGuiTexture.EMPTY;
    }

    /** This object's registry type name (from its {@link FXObjectType}). */
    default String name() {
        return getFXObjectType().name();
    }

    /**
     * The invariant, type-level definition for this object kind (creator + icon + animatable properties).
     * Each concrete fx-object class returns its own registered {@link FXObjectType} singleton.
     */
    FXObjectType getFXObjectType();

    /**
     * emitter name unique for one project
     */
    String getName();

    void setName(String name);

    void setDelay(int delay);

    Level getLevel();

    void setLevel(Level level);

    /**
     * Particle-engine retention: whether the engine should keep this object (vanilla {@code Particle}
     * contract). Includes runtime keep-alive (timeline not finished). For "is this object still doing
     * something", use {@link #isPlaying()}.
     */
    boolean isAlive();

    /**
     * Whether this object still contributes activity to its FX: it is {@link #isActive() active} and is
     * (or has a descendant that is) emitting/showing something. Drives {@code FXRuntime.isFinished()}.
     * Unlike {@link #isAlive()}, a timeline-deactivated object is never playing, and runtime keep-alive
     * does not count.
     */
    boolean isPlaying();

    float getDeltaTime();

    @Override
    default String getConfigurableName() {
        return getName();
    }

    /**
     * should render particle. Hierarchical: hidden if this node is hidden by the manual eye-toggle
     * ({@link #isSelfVisible()}) or by the timeline ({@link #isSelfTimelineVisible()}), or if any
     * ancestor is hidden.
     */
    default boolean isVisible() {
        if (!isEnabled() || !isSelfVisible() || !isSelfTimelineVisible()) return false;
        var parent = transform().parent();
        if (parent != null && parent.sceneObject() instanceof IFXObject ifxObject) {
            return ifxObject.isVisible();
        }
        return true;
    }

    /**
     * Whether this node is active (ticking). Hierarchical: an inactive node and all its descendants
     * neither tick nor render. Driven at runtime by the timeline's activator/control tracks; defaults
     * to active for objects the timeline does not control.
     */
    default boolean isActive() {
        if (!isEnabled() || !isSelfActive()) return false;
        var parent = transform().parent();
        if (parent != null && parent.sceneObject() instanceof IFXObject ifxObject) {
            return ifxObject.isActive();
        }
        return true;
    }

    /**
     * The saved switch: off takes this object and everything under it out of the effect, whatever the
     * timeline says. On by default.
     */
    default boolean isEnabled() {
        return true;
    }

    /** set/get this node's own active flag (timeline-driven, not persisted). */
    void setSelfActive(boolean active);

    boolean isSelfActive();

    /** This node's own playback-speed multiplier (timeline-driven by the speed track, not persisted,
     *  default 1). Clamped {@code >=0} when composed; {@code 0} freezes the node. */
    void setSelfTimeScale(float timeScale);

    float getSelfTimeScale();

    /**
     * Hierarchical playback speed: this node's own scale times every ancestor's. Drives the per-tick
     * simulation {@code dt} (see {@code FXObject.tick}); inherited by descendants exactly like
     * {@link #isActive()}/{@link #isVisible()}.
     */
    default float timeScale() {
        var self = Math.max(0, getSelfTimeScale());
        var parent = transform().parent();
        if (parent != null && parent.sceneObject() instanceof IFXObject ifxObject) {
            return self * ifxObject.timeScale();
        }
        return self;
    }

    /** set/get this node's own timeline-visibility flag (timeline-driven, not persisted). */
    void setSelfTimelineVisible(boolean visible);

    boolean isSelfTimelineVisible();

    /**
     * set fx self visible
     */
    void setSelfVisible(boolean visible);

    /**
     * is fx self visible
     */
    boolean isSelfVisible();

    void setEffect(IEffectExecutor effect);

    @Nullable
    IEffectExecutor getEffectExecutor();

    /**
     * force - remove without waiting.
     */
    void remove(boolean force);

    /**
     * reset runtime data
     */
    void reset();

    /**
     * Report what this object's first frame would otherwise load lazily, such as the program variants and
     * textures its renderer uses (see {@code FXHelper.preload}). Called on the render thread, on the cached
     * definition that runtimes are copied from. Does nothing by default.
     */
    default void preload(PreloadContext context) {
    }

    /**
     * copy this object
     */
    default IFXObject deepCopy() {
        return CODEC.encodeStart(NbtOps.INSTANCE, this).result().flatMap((tag) -> CODEC.parse(NbtOps.INSTANCE, tag).result()).orElse(null);
    }

    default IFXObject shallowCopy() {
        return deepCopy();
    }

    /**
     * deep copy this object
     */
    default IFXObject copy(boolean deep) {
        return deep ? deepCopy() : shallowCopy();
    }

    /**
     * Emit this object into the executor's level: resets its runtime state and adds it (as a vanilla
     * {@link Particle}) to the owning particle engine, which drives tick/render/removal from then on.
     */
    default void emit(@Nonnull IEffectExecutor effect) {
        emit(effect, null, null, null);
    }

    /** @deprecated typo; use {@link #emit(IEffectExecutor)}. */
    @Deprecated
    default void emmit(@Nonnull IEffectExecutor effect) {
        emit(effect);
    }

    /** @deprecated typo; use {@link #emit(IEffectExecutor, Vector3f, Quaternionf, Vector3f)}. */
    @Deprecated
    default void emmit(@Nonnull IEffectExecutor effect, @Nullable Vector3f position, @Nullable Quaternionf rotation, @Nullable Vector3f scale) {
        emit(effect, position, rotation, scale);
    }

    /**
     * Emit this object with an optional transform override. See {@link #emit(IEffectExecutor)}.
     */
    default void emit(@Nonnull IEffectExecutor effect, @Nullable Vector3f position, @Nullable Quaternionf rotation, @Nullable Vector3f scale) {
        setEffect(effect);
        reset();
        if (position != null) {
            updatePos(position);
        }
        if (rotation != null) {
            updateRotation(rotation);
        }
        if (scale != null) {
            updateScale(scale);
        }
        setLevel(effect.getLevel());
        if (this instanceof Particle particle) {
            if (effect.getLevel() instanceof DummyWorld dummyWorld) {
                dummyWorld.getParticleManager().addParticle(particle);
            } else {
                Minecraft.getInstance().particleEngine.add(particle);
            }
        }
    }

    // transform
    default void updatePos(Vector3f newPos) {
        transform().position(newPos);
    }

    default void updateRotation(Quaternionf newRot) {
        transform().rotation(newRot);
    }

    default void updateRotation(Vector3f newRot) {
        transform().rotation(new Quaternionf().rotationXYZ(newRot.x, newRot.y, newRot.z));
    }

    default void updateScale(Vector3f newScale) {
        transform().scale(newScale);
    }

    default void copyTransformFrom(IFXObject fxObject) {
        transform().copyTransformFrom(fxObject.transform(), true, true);
        transform()._setInternalID(fxObject.transform().id());
    }

    // information inspection
    default void inspectSceneInformation(SceneView sceneView, UIElement container) {

    }

    // editor rendering
    default void drawEditorAfterWorld(SceneView.ParticleSceneEditor scene, MultiBufferSource bufferSource, float partialTicks) {

    }
}
