package com.lowdragmc.photon.client.gameobject.emitter.data;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigList;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.annotation.ReadOnlyManaged;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.collision.BlockFilter;
import com.lowdragmc.photon.client.collision.CollisionEvent;
import com.lowdragmc.photon.client.collision.Contact;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.SpawnChain;
import com.lowdragmc.photon.client.gameobject.RuntimeValue;
import com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.RandomConstant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.CurveConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.RandomCurve;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import com.lowdragmc.photon.client.util.LDLibPersistenceCompat;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author KilaBash
 * @date 2023/7/17
 * @implNote SubEmittersSetting
 */
@OnlyIn(Dist.CLIENT)
@Setter
@Getter
public class SubEmittersSetting extends ToggleGroup {

    @Configurable(name = "SubEmittersSetting.emitters")
    @ConfigList(configuratorMethod = "buildEmitterConfigurator", addDefaultMethod = "addDefaultEmitter")
    @ReadOnlyManaged(serializeMethod = "emittersSerialize", deserializeMethod = "emittersDeserialize")
    protected List<Emitter> emitters = new ArrayList<>();

    public Runtime createRuntime() {
        return new Runtime(this);
    }

    public static class Runtime {
        private final SubEmittersSetting config;
        public final RuntimeValue<Boolean> enable;
        public final RuntimeValue<List<Emitter>> emitters; // slot only (list → no timeline binding)

        public Runtime(SubEmittersSetting config) {
            this.config = config;
            this.enable = new RuntimeValue<>(config::isEnable);
            this.emitters = new RuntimeValue<>(config::getEmitters);
        }

        public boolean isEnable() {
            return enable.get();
        }

        public void triggerEvent(TileParticle father, Event event) {
            triggerEvent(father, event, null);
        }

        /** {@code contact} is the collision behind the event, if any: emitters can spawn at its point. */
        public void triggerEvent(TileParticle father, Event event, @Nullable CollisionEvent contact) {
            for (Emitter candidate : emitters.get()) {
                if (candidate.event == event && candidate.accepts(contact)) {
                    candidate.spawnEmitter(father, contact);
                }
            }
        }

        /**
         * Tick event with integer-tick-crossing semantics: fires when this step crossed a whole tick
         * and that tick matches the candidate's tickInterval. Robust under fractional dt (sub-stepped
         * timeline speed), where the particle's float age almost never lands exactly on an integer.
         */
        public void triggerTickEvent(TileParticle father, float dt) {
            var newTick = Mth.floor(father.getAge());
            if (newTick <= Mth.floor(father.getAge() - dt)) {
                return; // no whole tick crossed this step (dt <= 1 => at most one crossing)
            }
            for (Emitter candidate : emitters.get()) {
                if (candidate.event == Event.Tick && newTick % Math.max(1, candidate.tickInterval) == 0) {
                    candidate.spawnEmitter(father);
                }
            }
        }

        public void clear() {
            enable.clear();
            emitters.clear();
        }
    }

    private Configurator buildEmitterConfigurator(Supplier<Emitter> getter, Consumer<Emitter> setter) {
        var instance = getter.get();
        if (instance != null && instance.createDirectConfigurator() instanceof ConfiguratorGroup group) {
            group.setCollapse(false);
            group.lineContainer.setDisplay(false);
            return group;
        }
        return new Configurator();
    }

    private Emitter addDefaultEmitter() {
        var emitter = new Emitter();
        emitter.spawnAtContact = true;
        return emitter;
    }

    public IntTag emittersSerialize(List<Emitter> emitters) {
        return IntTag.valueOf(LDLibPersistenceCompat.checkedManagedListCount(emitters.size(), "sub-emitter"));
    }

    public List<Emitter> emittersDeserialize(Tag uid) {
        if (uid instanceof IntTag count) {
            int size = LDLibPersistenceCompat.checkedManagedListCount(count.getAsInt(), "sub-emitter");
            var values = new ArrayList<Emitter>(size);
            for (int i = 0; i < size; i++) values.add(new Emitter());
            return values;
        }
        if (!(uid instanceof ListTag tag)) {
            throw new IllegalArgumentException("Unsupported managed SubEmittersSetting list UID: " + uid.getClass().getSimpleName());
        }
        LDLibPersistenceCompat.checkedManagedListCount(tag.size(), "sub-emitter");
        var groups = new ArrayList<Emitter>();
        for (CompoundTag emitterTag : LDLibPersistenceCompat.decodePayloadList(tag)) {
            var emitter = new Emitter();
            emitter.deserializeNBT(Platform.getFrozenRegistry(), emitterTag);
            groups.add(emitter);
        }
        return groups;
    }

    public enum Event {
        Birth,
        Death,
        Collision,
        FirstCollision,
        Tick,
        TriggerEnter,
        TriggerExit;

        /** Whether a contact can be behind it, which is what {@link Surface} filters. */
        public boolean fromContact() {
            return this != Birth && this != Tick;
        }
    }

    public enum Surface {
        Any,
        Blocks,
        Fluids,
        Entities,
        Colliders
    }

    @Setter
    @Getter
    public static class Emitter implements IConfigurable, IPersistedSerializable {
        @Nullable
        @Persisted
        protected ResourceLocation fxLocation = null;
        /** Name of an embedded Photon 1 subeffect; ordinary Photon 2 references use fxLocation. */
        @Nullable
        @Persisted
        protected String legacySubFXName;

        @Override
        public void deserializeNBT(net.minecraft.core.HolderLookup.@org.jetbrains.annotations.NotNull Provider provider,
                                   net.minecraft.nbt.@org.jetbrains.annotations.NotNull CompoundTag tag) {
            IPersistedSerializable.super.deserializeNBT(provider, tag);
            if (legacySubFXName == null && tag.contains("emitter", net.minecraft.nbt.Tag.TAG_STRING)) {
                legacySubFXName = tag.getString("emitter");
                if (!tag.contains("legacyTickGate")) legacyTickGate = true;
            }
        }
        /** Photon 1 applied tickInterval to every event, not just Tick. */
        @Persisted
        protected boolean legacyTickGate;
        @Configurable(name = "Emitter.event", tips = "photon.emitter.config.sub_emitters.emitter.event")
        protected Event event = Event.Birth;
        @Configurable(name = "Emitter.emitProbability", tips = "photon.emitter.config.sub_emitters.emitter.emit_probability")
        @NumberFunctionConfig(types = {Constant.class, RandomConstant.class, Curve.class, RandomCurve.class}, min = 0, max = 1, curveConfig = @CurveConfig(bound = {0, 1}, xAxis = "probability", yAxis = "lifetime"))
        protected NumberFunction emitProbability = NumberFunction.constant(0);
        @Configurable(name = "Emitter.tickInterval", tips = "photon.emitter.config.sub_emitters.emitter.tick_interval")
        @ConfigNumber(range = {1, Integer.MAX_VALUE})
        protected int tickInterval = 1;
        @Configurable(name = "Emitter.inheritColor", tips = "photon.emitter.config.sub_emitters.emitter.inherit_color")
        protected boolean inheritColor = false;
        @Configurable(name = "Emitter.inheritSize", tips = "photon.emitter.config.sub_emitters.emitter.inherit_size")
        protected boolean inheritSize = false;
        @Configurable(name = "Emitter.inheritRotation", tips = "photon.emitter.config.sub_emitters.emitter.inherit_rotation")
        protected boolean inheritRotation = false;
        @Configurable(name = "Emitter.inheritLifetime", tips = "photon.emitter.config.sub_emitters.emitter.inherit_lifetime")
        protected boolean inheritLifetime = false;
        @Configurable(name = "Emitter.inheritDuration", tips = "photon.emitter.config.sub_emitters.emitter.inherit_duration")
        protected boolean inheritDuration = false;
        /** False for entries saved before the option existed; new entries start with it on. */
        @Configurable(name = "Emitter.spawnAtContact", tips = "photon.emitter.config.sub_emitters.emitter.spawn_at_contact")
        protected boolean spawnAtContact = false;
        @Configurable(name = "Emitter.alignToNormal", tips = "photon.emitter.config.sub_emitters.emitter.align_to_normal")
        protected boolean alignToNormal = false;
        @Configurable(name = "Emitter.surface", tips = "photon.emitter.config.sub_emitters.emitter.surface")
        protected Surface surface = Surface.Any;
        @Configurable(name = "Emitter.surfaceBlocks", tips = "photon.emitter.config.sub_emitters.emitter.surface_blocks")
        @ConfigList(configuratorMethod = "buildBlockEntry", addDefaultMethod = "addEmptyEntry")
        @ReadOnlyManaged(serializeMethod = "stringsSerialize", deserializeMethod = "stringsDeserialize")
        protected List<String> surfaceBlocks = new ArrayList<>();
        /** The resolved {@link #surfaceBlocks}; read from particle workers, so replaced whole, never changed. */
        @Nullable
        private volatile SurfaceFilter surfaceFilter;

        private record SurfaceFilter(List<String> entries, int tags, @Nullable BlockFilter filter) {
        }

        /** Whether the contact behind an event passes {@link #surface}; events without one pass only {@link Surface#Any}. Any thread. */
        public boolean accepts(@Nullable CollisionEvent contact) {
            var surface = getSurface();
            if (surface == Surface.Any || event == null || !event.fromContact()) return true;
            if (contact == null) return false;
            byte kind = switch (surface) {
                case Blocks -> Contact.BLOCK;
                case Fluids -> Contact.FLUID;
                case Entities -> Contact.ENTITY;
                default -> Contact.COLLIDER;
            };
            if (contact.kind() != kind) return false;
            if (surface != Surface.Blocks && surface != Surface.Fluids) return true;
            var filter = blockFilter();
            return filter == null || (contact.target() instanceof BlockState state && filter.test(state));
        }

        public Surface getSurface() {
            return surface == null ? Surface.Any : surface;
        }

        @Nullable
        private BlockFilter blockFilter() {
            var cached = surfaceFilter;
            int tags = BlockFilter.tagGeneration();
            if (cached == null || cached.tags != tags || !cached.entries.equals(surfaceBlocks)) {
                var entries = List.copyOf(surfaceBlocks.stream().map(entry -> entry == null ? "" : entry).toList());
                cached = new SurfaceFilter(entries, tags, BlockFilter.of(entries, List.of()));
                surfaceFilter = cached;
            }
            return cached.filter;
        }

        private String addEmptyEntry() {
            return "";
        }

        private Configurator buildBlockEntry(Supplier<String> getter, Consumer<String> setter) {
            return IdLists.blockEntry(getter, setter);
        }

        public ListTag stringsSerialize(List<String> values) {
            return IdLists.serialize(values);
        }

        public List<String> stringsDeserialize(ListTag tag) {
            return IdLists.deserialize(tag);
        }

        public void spawnEmitter(TileParticle father) {
            spawnEmitter(father, null);
        }

        public void spawnEmitter(TileParticle father, @Nullable CollisionEvent contact) {
            if (legacyTickGate && Mth.floor(father.getAge()) % Math.max(1, tickInterval) != 0) return;
            if (fxLocation == null && (legacySubFXName == null || legacySubFXName.isBlank())) {
                return;
            }
            // probability roll stays here: the particle's RandomSource is confined to its update thread
            boolean spawns = father.getRandomSource().nextFloat() < emitProbability.get(father.getT(0),
                    () -> father.getMemRandom("sub_emitter_probability")).floatValue();
            // FXHelper.getFX + FXRuntime.emit (particle engine) are not thread-safe: particle updates
            // may run on parallelStream workers, so defer the spawn to the game thread — the owning
            // emitter drains it at the end of the same tick (deferred in serial mode too, so ordering
            // is mode-independent; a dead particle's fields stay valid until next tick's removal).
            if (father.getEmitter() instanceof ParticleEmitter particleEmitter) {
                // a de-duplicated contact counts as reported whether or not this roll spawns
                boolean dedupes = contact != null && particleEmitter.dedupes(contact);
                if (!spawns && !dedupes) return;
                particleEmitter.scheduleSubEmitterSpawn(father.nextSpawnOrder(), () -> {
                    if (dedupes && !particleEmitter.passesDedupe(father, contact)) return;
                    if (spawns) doSpawn(father, contact);
                });
            } else if (spawns) {
                doSpawn(father, contact); // non-ParticleEmitter owners never parallel-update TileParticles
            }
        }

        private void doSpawn(TileParticle father, @Nullable CollisionEvent contact) {
            var executor = father.getEmitter().getEffectExecutor();
            var fx = legacySubFXName != null && executor != null ? executor.legacySubEffect(legacySubFXName) : null;
            var id = fx != null ? SpawnChain.legacyId(legacySubFXName) : fxLocation != null ? SpawnChain.id(fxLocation) : null;
            if (fx == null && fxLocation != null) fx = FXHelper.getFX(fxLocation);
            if (fx == null || id == null) return;
            // an effect spawning itself (directly or a -> b -> a) would multiply without end: refuse the spawn
            var parentChain = father.getEmitter().getScene() instanceof FXRuntime parent ? parent.spawnChain() : List.<String>of();
            var roots = executor != null ? executor.playingAs() : List.<String>of();
            var refusal = SpawnChain.refusal(roots, parentChain, id);
            if (refusal != null) {
                refuse(id, refusal);
                return;
            }
            var runtime = fx.createRuntime();
            runtime.spawnChain(SpawnChain.extend(parentChain, id));
            runtime.root.updatePos(contact != null && spawnAtContact ? contact.position() : father.getWorldPos());
            if (contact != null && alignToNormal) {
                var normal = contact.normal();
                // trigger exits have no surface, so no normal
                if (normal.lengthSquared() > 0.5f) {
                    runtime.root.updateRotation(new Quaternionf().rotationTo(0, 1, 0, normal.x, normal.y, normal.z));
                }
            }
            // emit() resets each object and its runtime slots. Apply inheritance afterwards or the
            // inherited age and duration are immediately erased before the first simulation tick.
            runtime.emit(father.getEmitter().getEffectExecutor());
            applyInheritance(runtime, father);
        }

        /** Apply parent particle properties to this runtime only, after its objects have been reset. */
        public void applyInheritance(com.lowdragmc.photon.client.fx.FXRuntime runtime, TileParticle father) {
            for (var value : runtime.objects.values()) {
                if (value instanceof IParticleEmitter particleEmitter) {
                    if (inheritDuration) {
                        particleEmitter.inheritDuration(father.getLifetime());
                    }
                    if (inheritLifetime) {
                        particleEmitter.setAge((int) father.getAge());
                    }
                    if (inheritColor) {
                        particleEmitter.setRGBAColor(father.getRealColor(0));
                    }
                    if (inheritSize) {
                        particleEmitter.transform().scale(father.getRealSize(0));
                    }
                    if (inheritRotation) {
                        var xyz = father.getRealRotation(0);
                        particleEmitter.transform().rotation(new Quaternionf().rotationXYZ(xyz.x, xyz.y, xyz.z));
                    }
                }
            }
        }

        /** Refused spawns already explained in the log, so a refused sub-emitter costs one line, not one per particle. */
        private static final Set<String> REFUSED = ConcurrentHashMap.newKeySet();

        private static void refuse(String id, String reason) {
            if (REFUSED.size() > 256) REFUSED.clear();
            if (REFUSED.add(id + " " + reason)) {
                Photon.LOGGER.warn("photon: sub-emitter spawn of {} skipped: {}", id, reason);
            }
        }

        /**
         * The id through which spawning {@code fx} from an effect known as {@code self} would spawn that effect
         * inside itself ({@code fx} itself, or an effect {@code fx} spawns at any depth, by sub-emitter), or null.
         * Reads the effects it visits through {@link FXHelper#getFX}; walks at most {@code limit} of them.
         */
        @Nullable
        public static ResourceLocation cycleThrough(Collection<ResourceLocation> self, ResourceLocation fx, int limit) {
            if (self.isEmpty()) return null;
            if (self.contains(fx)) return fx;
            var visited = new HashSet<ResourceLocation>();
            var queue = new ArrayDeque<ResourceLocation>();
            queue.add(fx);
            visited.add(fx);
            while (!queue.isEmpty() && visited.size() <= limit) {
                var at = FXHelper.getFX(queue.poll());
                if (at == null) continue;
                for (var spawned : spawnsOf(at)) {
                    if (self.contains(spawned)) return spawned;
                    if (visited.add(spawned)) queue.add(spawned);
                }
            }
            return null;
        }

        /** The effect files the sub-emitters of {@code fx} (and of its embedded Photon 1 sub-effects) spawn. */
        public static Set<ResourceLocation> spawnsOf(FX fx) {
            var out = new LinkedHashSet<ResourceLocation>();
            collectSpawns(fx, out, new HashSet<>());
            return out;
        }

        private static void collectSpawns(FX fx, Set<ResourceLocation> out,
                                          Set<FX> visited) {
            if (!visited.add(fx)) return;
            for (var object : fx.getFxData().objects()) {
                if (object instanceof ParticleEmitter emitter) {
                    for (var sub : emitter.config.subEmitters.getEmitters()) {
                        if (sub.getFxLocation() != null) out.add(sub.getFxLocation());
                    }
                }
            }
            for (var legacy : fx.getLegacySubFXs().values()) collectSpawns(legacy, out, visited);
        }

        @Override
        public void buildConfigurator(ConfiguratorGroup father) {
            var picker = new AtomicReference<UIElement>();
            var configurator = new SearchComponentConfigurator<>("fx",
                    () -> fxLocation != null ? fxLocation.toString() : legacySubFXName == null ? "" : legacySubFXName,
                    v -> {
                        var picked = (v == null || v.isEmpty()) ? null : ResourceLocation.tryParse(v);
                        if (picked != null && refusedAsCycle(picked, picker.get())) return;
                        legacySubFXName = null;
                        fxLocation = picked;
                    },
                    "", true,
                    FXHelper::searchFX,
                    s -> s,
                    UIElementProvider.text(s -> Component.literal(s == null || s.isEmpty() ? "---" : s)));
            picker.set(configurator);
            father.addConfigurators(configurator.setTips("photon.emitter.config.sub_emitters.emitter.name"));
            IConfigurable.super.buildConfigurator(father);
        }

        /**
         * Whether {@code picked}, chosen in the editor, would spawn the edited effect inside itself; then the pick is
         * refused and explained. Only an editor knows the effect's ids, so outside one nothing is refused here (the
         * spawn itself is still refused when it would run, see {@link #doSpawn}).
         */
        private static boolean refusedAsCycle(ResourceLocation picked, @Nullable UIElement from) {
            for (var element = from; element != null; element = element.getParent()) {
                if (!(element instanceof FXEditor editor)) continue;
                if (!(editor.getCurrentProject() instanceof FXProject project)) return false;
                var self = new LinkedHashSet<>(project.getKnownAs());
                if (project.getFx().getFxLocation() != null) self.add(project.getFx().getFxLocation());
                var through = cycleThrough(self, picked, 64);
                if (through == null) return false;
                refuse(SpawnChain.id(picked), "it is already playing further out (" + picked + " -> " + through + " would never end)");
                var dialog = new Dialog().setTitle("photon.emitter.config.sub_emitters.emitter.cycle.title");
                var label = new Label();
                label.setText(Component.translatable("photon.emitter.config.sub_emitters.emitter.cycle", picked.toString(), through.toString()));
                label.textStyle(style -> style.textWrap(TextWrap.WRAP).adaptiveHeight(true));
                label.layout(layout -> layout.widthPercent(100));
                dialog.addContent(label);
                dialog.addButton(new Button().setOnClick(e -> dialog.close())
                        .setText("ldlib.gui.tips.confirm").addClass("__confirm-button__"));
                dialog.show(editor);
                return true;
            }
            return false;
        }
    }
}


