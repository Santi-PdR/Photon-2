package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The context an {@link com.lowdragmc.photon.client.fx.FXRuntime} plays inside: supplies the level
 * and receives per-tick / per-frame callbacks from every emitted {@link IFXObject}.
 * <p>
 * Implementations anchor the FX to something and keep it in sync (or kill it): e.g.
 * {@link EntityEffectExecutor} follows an entity, {@link BlockEffectExecutor} a block, and the
 * editor's {@code FXProjectEffectExecutor} just provides a level + seeded RNG for previews.
 */
@OnlyIn(Dist.CLIENT)
public interface IEffectExecutor {

    Level getLevel();

    /** Whether this execution may emit timeline signals and audio. */
    default boolean allowTimelineEvents() {
        return true;
    }

    /**
     * update each FX objects during their duration, per tick. Execute low frequency logic here.
     * <br>
     * e.g., kill particle
     * @param fxObject fx object
     */
    default void updateFXObjectTick(IFXObject fxObject) {
    }

    /**
     * update each FX objects during rendering, per frame. Execute high frequency logic here.
     * <br>
     * e.g., update emitter position, rotation, scale
     * @param fxObject fx object
     * @param partialTicks partialTicks
     */
    default void updateFXObjectFrame(IFXObject fxObject, float partialTicks) {

    }

    default RandomSource getRandomSource() {
        return getLevel().random;
    }

    /**
     * The entity this effect belongs to, if any: beam raycasts skip it, and so does particle collision while its
     * "ignore owner" option is on, so a mob's own effect does not bounce off the mob. Game thread.
     */
    @Nullable
    default Entity owner() {
        return null;
    }

    /** Embedded named subeffects from a Photon 1 definition, when present. */
    @Nullable
    default FX legacySubEffect(String name) {
        return this instanceof IFXEffectExecutor whole ? whole.getFx().getLegacySubFXs().get(name) : null;
    }

    /**
     * The ids ({@link SpawnChain} form) of the effect this executor plays — its file's location here; the
     * editor adds what its project was imported from and exported to. A sub-emitter inside it that spawns
     * one of them would spawn the effect inside itself without end, so {@code SubEmittersSetting} refuses
     * it. Empty when unknown, which only loses that first-level check (the chain still stops the cycle one
     * level further in).
     */
    default List<String> playingAs() {
        if (this instanceof IFXEffectExecutor whole && whole.getFx().getFxLocation() != null) {
            return List.of(SpawnChain.id(whole.getFx().getFxLocation()));
        }
        return List.of();
    }

    /**
     * Called when a timeline {@code signal} track fires a signal during live forward playback. Override
     * to react per-effect; global subscribers can also listen via {@code PhotonSignals}.
     *
     * @param channel the signal track's display name (channel)
     * @param name    the signal's name (may repeat across signals)
     * @param data    the signal's custom data
     * @param time    the master-clock tick at which it fired
     */
    default void onTimelineSignal(String channel, String name, CompoundTag data, double time) {
    }

    /** The post-effect request sink this execution context feeds — timeline PostProcess clips
     *  submit here every frame. The editor executor overrides to its isolated scene stack. */
    default com.lowdragmc.photon.client.postfx.runtime.PostEffectStack postEffectSink() {
        return com.lowdragmc.photon.client.postfx.runtime.PostEffectStack.GLOBAL;
    }
}

