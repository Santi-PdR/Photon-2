package com.lowdragmc.photon.client.fx;

import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.IScene;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.ISceneObject;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.fx.timeline.TimelinePlayer;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.FXInstanceObject;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * A live, playable instance of an {@link FX} definition.
 * <p>
 * Created via {@link FX#createRuntime()}, it owns the object tree (rooted at an always-present
 * {@link EmptyFXObject} with id {@link #ROOT_UUID}) and the {@link TimelinePlayer}. Playback starts
 * with {@link #emit(IEffectExecutor)}, which hands every object to the owning particle engine; the
 * engine then drives ticking/rendering, while the root object drives the timeline clock.
 */
@Getter
public class FXRuntime implements IScene {
    public final static UUID ROOT_UUID = new UUID(0, 0);
    public final FXData fxData;
    public final Map<UUID, IFXObject> objects = new LinkedHashMap<>();
    public final IFXObject root;
    public final TimelinePlayer timelinePlayer;
    @Getter(AccessLevel.NONE)
    private final List<FXInstanceObject> instances = new ArrayList<>();
    @Getter(AccessLevel.NONE)
    private boolean beginningInstances;
    /** Whether {@link #emit} ran (a runtime is never finished/valid before its first emit). */
    private boolean emitted = false;
    /** Whether {@link #destroy} ran (re-{@link #emit}ting revives the runtime, e.g. editor replay). */
    private boolean destroyed = false;
    /** Whether a non-forced {@link #destroy} is still playing the timeline's release section. */
    private boolean releasing;
    /** The particle engine this runtime was emitted into (vanilla or an editor manager); see {@link #isValid()}. */
    @Nullable
    private ParticleTickHost host;
    /** The host's wipe generation at emit time; a later generation means our particles were discarded. */
    private int generationAtEmit;
    /** Playback rate for this whole runtime; see {@link #setRate}. */
    private float rate = 1f;

    public FXRuntime(FXData fxData) {
        this.fxData = fxData;
        // created before any object is added, so addSceneObjectInternal is the ONE place that wires
        // per-object keep-alive — for construction and editor additions alike
        this.timelinePlayer = new TimelinePlayer(this, fxData.timeline());
        this.root = new EmptyFXObject();
        this.root.transform()._setInternalID(ROOT_UUID);
        addSceneObject(root);
        root.setName("root");
        initRuntime();
        // the always-on root drives the timeline once per tick (editor + in-world)
        if (root instanceof FXObject fxRoot) {
            fxRoot.setOnUpdateTick(() -> {
                // ⚠️ Re-applied every tick, not only in setRate: FXObject.reset() puts selfTimeScale
                // back to 1, and a control clip restarting a subtree resets the root with it. Runs
                // before the timeline's own speed tracks (evaluate -> applySpeed), so a track that
                // targets the root deliberately wins for that tick — see setRate.
                root.setSelfTimeScale(rate);
                if (!timelinePlayer.isEmpty()) {
                    timelinePlayer.tick(rate);
                }
                // the release section played out (or the timeline is empty): now let the remnants finish
                if (releasing && timelinePlayer.isFinished()) {
                    windDownAll();
                }
            });
            // and the per-frame animation pass for smooth (interpolated) transform animation
            fxRoot.setOnUpdateFrame(partialTicks -> {
                if (!timelinePlayer.isEmpty()) {
                    timelinePlayer.frame(partialTicks);
                }
            });
        }
    }

    private void initRuntime() {
        for (var fxObject : fxData.objects()) {
            addSceneObjectInternal(fxObject);
        }
        for (var fxObject : fxData.objects()) {
            fxObject.setScene(this);
        }
        for (var fxObject : fxData.objects()) {
            if (fxObject.transform().parent() == null) {
                fxObject.transform().parent(root.transform(), false);
            }
            fxObject.transform().rebuildChildOrder();
        }
    }

    /**
     * The editor's synthetic root is not in {@link FXData#objects()} and is not written on Save.
     * Gizmo / inspector edits of the whole FX (move, rotate, scale the root) would otherwise
     * vanish. Fold that pose into each direct child, relative to {@code restPosition}, then put
     * the root back at rest so the scene still matches what will play in the world.
     */
    public void bakeRootIntoChildren(Vector3f restPosition) {
        var rootTransform = root.transform();
        var position = rootTransform.position();
        var scale = rootTransform.scale();
        var rotation = rootTransform.rotation();
        boolean moved = position.distanceSquared(restPosition) > 1e-8f;
        boolean scaled = Math.abs(scale.x - 1f) + Math.abs(scale.y - 1f) + Math.abs(scale.z - 1f) > 1e-4f;
        boolean rotated = rotation.x * rotation.x + rotation.y * rotation.y + rotation.z * rotation.z > 1e-8f;
        if (!moved && !scaled && !rotated) {
            return;
        }
        var children = new ArrayList<>(rootTransform.children());
        for (var child : children) {
            child.parent(null, true);
        }
        root.updatePos(new Vector3f(restPosition));
        root.updateRotation(new Quaternionf());
        root.updateScale(new Vector3f(1, 1, 1));
        for (var child : children) {
            child.parent(rootTransform, true);
        }
    }

    @Nullable
    @Override
    public ISceneObject getSceneObject(UUID uuid) {
        // unknown ids return null (a stale parent ref is re-parented to root by initRuntime, and
        // Transform.awake tolerates null); silently substituting the root here masked lookup bugs
        return objects.get(uuid);
    }

    @Override
    public Collection<ISceneObject> getAllSceneObjects() {
        return objects.values().stream().map(ISceneObject.class::cast).toList();
    }

    @Override
    public void addSceneObjectInternal(ISceneObject sceneObject) {
        if (sceneObject instanceof IFXObject fxObject) {
            var previous = objects.put(fxObject.id(), fxObject);
            if (fxObject instanceof FXInstanceObject instance && !instances.contains(instance)) {
                instances.add(instance);
            }
            if (previous != null) {
                if (previous != fxObject) {
                    Photon.LOGGER.warn("Duplicate fx runtime object id %s is replaced".formatted(fxObject.id()));
                    if (previous instanceof FXInstanceObject replaced) {
                        instances.remove(replaced);
                        replaced.stopAudio();
                    }
                }
            }
            // while the timeline has future content, every object must stay in the particle engine:
            // the root to drive the clock, the rest so clips can reactivate/restart them (an empty
            // timeline is always finished, so the supplier is inert for legacy FX)
            if (fxObject instanceof FXObject object) {
                // ... and an FX instance's timeline keeps what the instance placed, the same way
                object.setKeepAlive(() -> !timelinePlayer.isFinished() || hasInstances() && FXInstanceObject.keepsAlive(object));
            }
        } else {
            throw new IllegalArgumentException("%s is not an instance of IFXObject".formatted(sceneObject));
        }
    }

    @Override
    public void removeSceneObjectInternal(ISceneObject sceneObject) {
        if (sceneObject instanceof IFXObject fxObject) {
            objects.remove(fxObject.id());
            if (fxObject instanceof FXInstanceObject instance) {
                instances.remove(instance);
                instance.stopAudio();
            }
        } else {
            throw new IllegalArgumentException("%s is not an instance of IFXObject".formatted(sceneObject));
        }
    }

    /**
     * <b>How fast this whole playback runs</b> — 1 is normal, 0.5 half speed, 0 frozen. Both halves
     * of "the FX's time" are scaled together:
     *
     * <ul>
     *   <li>the objects' simulation, through the root's {@code selfTimeScale}, which every
     *       descendant inherits ({@link IFXObject#timeScale()}) and which
     *       {@code FXObject.tick} spends as its per-tick {@code dt};</li>
     *   <li>the <b>timeline's master clock</b> ({@link TimelinePlayer#tick(float)}), so activator,
     *       control, animation, signal and audio clips keep their places relative to the particles.
     *       Without this half a slowed FX ran its particles slowly and its timeline at full speed.</li>
     * </ul>
     *
     * <p><b>Cost</b>: above 1 the simulation sub-steps ({@code ceil(rate)} steps of {@code dt <= 1},
     * capped at {@code FXObject.MAX_SUBSTEPS}), which is the cost of the extra simulated time and
     * nothing more; at or below 1 it is one step per tick, exactly as before. A rate of 1 changes
     * no behaviour at all.
     *
     * <p>⚠️ Not persisted and not the timeline's: an FX whose own <b>speed track</b> targets the
     * root overwrites this for the ticks that track covers ({@code TimelinePlayer.applySpeed} runs
     * after the re-apply), which is the author's explicit intent winning over the caller's.
     *
     * <p>⚠️ Above 1 the timeline advances in steps larger than a tick, so a clip shorter than one
     * step can be stepped over; signals are not (they fire in the window {@code (last, now]}).
     */
    public void setRate(float rate) {
        this.rate = Math.max(0f, rate);
        root.setSelfTimeScale(this.rate);
    }

    /** Start playback: emit every object into the executor's particle engine and reset the timeline. */
    public void emit(IEffectExecutor effect) {
        emit(effect, 0);
    }

    public void emit(IEffectExecutor effect, int delay) {
        emit(effect, delay, null);
    }

    /**
     * Start playback with a start delay (in ticks). Every object is emitted once up front; the
     * timeline only gates active/visibility and restarts objects via the {@link TimelinePlayer}.
     * <p>
     * {@code startSection} starts the timeline at the beginning of a named section instead of 0, which is what makes
     * one effect usable as "start / loop / end": a combat effect can begin straight in its looping part. Null or an
     * unknown name starts at 0, as before. Ported from Photon 26.3.
     */
    public void emit(IEffectExecutor effect, int delay, @Nullable String startSection) {
        try {
            FXDiagnostics.playback(fxData, effect, delay);
        } catch (Exception diagnosticError) {
            Photon.LOGGER.warn("[fx-diagnostic] could not capture playback report", diagnosticError);
        }
        emitted = true;
        destroyed = false;
        releasing = false;
        // track the owning engine for isValid(): same resolution as IFXObject.emit's engine pick
        this.host = effect.getLevel() instanceof DummyWorld dummyWorld
                && dummyWorld.getParticleManager() instanceof ParticleTickHost tickHost
                ? tickHost : VanillaParticleHost.INSTANCE;
        this.generationAtEmit = host.generation();
        if (root instanceof FXObject fxRoot) {
            fxRoot.setTickHost(host);
            fxRoot.setLastHostTick(host.tickCount());
        }
        for (var fxObject : objects.values()) {
            try {
                fxObject.emit(effect);
                // set after emit: emit() resets the object, which zeroes its delay
                fxObject.setDelay(delay);
            } catch (RuntimeException error) {
                var location = effect instanceof FXEffectExecutor executor ? executor.getFx().getFxLocation() : null;
                FXDiagnostics.failure(location, "emit", "object " + fxObject.getName() + " (" + fxObject.id() + ") failed", null, error);
                throw error;
            }
        }
        timelinePlayer.begin(effect, startSection);
    }

    /**
     * Start the timeline of every FX instance that is active and has not started since its last reset, until
     * none is left: starting one can activate another inside it. Re-entrant calls return at once.
     */
    public void beginActiveInstances(IEffectExecutor effect) {
        if (instances.isEmpty() || beginningInstances) {
            return;
        }
        beginningInstances = true;
        try {
            boolean started;
            do {
                started = false;
                for (int i = 0; i < instances.size(); i++) {
                    if (instances.get(i).beginIfActive(effect)) {
                        started = true;
                    }
                }
            } while (started);
        } finally {
            beginningInstances = false;
        }
    }

    public boolean hasInstances() {
        return !instances.isEmpty();
    }

    /** Whether an FX instance's timeline is still playing, which outlasts this runtime's own. */
    public boolean hasRunningInstanceTimeline() {
        for (var instance : instances) {
            if (instance.isTimelinePlaying()) return true;
        }
        return false;
    }

    /** Silence what the FX instances' timelines play, which only their own evaluations would stop. */
    public void stopInstanceAudio() {
        instances.forEach(FXInstanceObject::stopAudio);
    }

    /** @deprecated typo; use {@link #emit(IEffectExecutor)}. */
    @Deprecated
    public void emmit(IEffectExecutor effect) {
        emit(effect);
    }

    /** @deprecated typo; use {@link #emit(IEffectExecutor, int)}. */
    @Deprecated
    public void emmit(IEffectExecutor effect, int delay) {
        emit(effect, delay);
    }

    /**
     * The FX has nothing left to do: the timeline has no future content AND no object is still
     * playing. A looping emitter never finishes on its own (destroy it via its executor). A destroyed
     * runtime finishes once its remnants drain (immediately when destroyed with {@code force}).
     */
    /** Sections of this effect's timeline; the entry point for jumping between them from a command. */
    public com.lowdragmc.photon.client.fx.timeline.SectionControl sections() {
        return timelinePlayer.sections();
    }

    public boolean isFinished() {
        return timelinePlayer.isFinished()
                && objects.values().stream().noneMatch(IFXObject::isPlaying);
    }

    /** Inverse of {@link #isFinished()} — "the FX is still running". Prefer isFinished() in new code. */
    public boolean isAlive() {
        return !isFinished();
    }

    /**
     * Whether this runtime is still a live effect. False before {@link #emit}, after {@link #destroy}
     * (even while remnants drain), and after the particle engine discarded its particles.
     * Callers who CACHE a runtime use this: the vanilla {@code ParticleEngine} silently discards all
     * particles on level change, {@code /photon_client clear_particles}, or when another mod reaches
     * in. O(1); a paused game does not invalidate (the heartbeat clock only advances when particles
     * actually tick).
     */
    public boolean isValid() {
        return !destroyed && isTracked();
    }

    /**
     * {@link #isValid()} minus "not destroyed": stays true while a destroyed effect's remnants play
     * out, so lights and particles that are still on screen are not treated as discarded. False before
     * {@link #emit} and after the engine wiped its particles.
     */
    public boolean isTracked() {
        if (!emitted) {
            return false;
        }
        if (host == null || !(root instanceof FXObject fxRoot)) {
            return true; // untrackable host: degrade to the state flags above
        }
        if (generationAtEmit != host.generation()) {
            return false;
        }
        // <=1: this may be queried mid-engine-tick, before the root has ticked this round
        return host.tickCount() - fxRoot.getLastHostTick() <= 1;
    }

    /**
     * Stop this runtime for good; it is no longer {@link #isValid() valid} from here on. {@code force}
     * drops everything at once. Otherwise the timeline's release section plays out first, if it has
     * one, then everything winds down: nothing new starts and remnants drain. A later {@link #emit}
     * revives the runtime (editor replay).
     */
    public void destroy(boolean force) {
        if (force) {
            destroyed = true;
            releasing = false;
            timelinePlayer.stop();
            for (var fxObject : objects.values()) {
                fxObject.remove(true);
            }
            return;
        }
        // a dead entity's executor calls this every tick
        if (destroyed) return;
        destroyed = true;
        // the timeline has a section to play on the way out: play it, and wind down when it finishes
        if (timelinePlayer.beginWindDown()) {
            releasing = true;
            return;
        }
        windDownAll();
    }

    /** Bring the effect down for good: nothing new starts, what is already out there is left to finish. */
    private void windDownAll() {
        releasing = false;
        timelinePlayer.stop();
        if (root instanceof FXObject fxRoot) {
            fxRoot.windDown();
        } else {
            for (var fxObject : objects.values()) {
                fxObject.remove(false);
            }
        }
    }

    /** Whether a non-forced {@link #destroy} is still playing the release section. */
    public boolean isReleasing() {
        return releasing;
    }

    @Nullable
    public IFXObject findObject(String name) {
        for (var fxObject : objects.values()) {
            if (fxObject.getName().equals(name)) {
                return fxObject;
            }
        }
        return null;
    }

    public List<IFXObject> findObjects(String name) {
        var list = new ArrayList<IFXObject>();
        for (var fxObject : objects.values()) {
            if (fxObject.getName().equals(name)) {
                list.add(fxObject);
            }
        }
        return list;
    }
}
