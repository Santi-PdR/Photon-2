package com.lowdragmc.photon.client.gameobject.emitter.data;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.fx.SpawnChain;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Smoke regression for {@link SpawnChain} and the sub-emitter spawn guard in {@link SubEmittersSetting.Emitter}:
 * an effect that spawns itself by sub-emitter — directly, or {@code ping -> pong -> ping} — used to start another
 * copy of itself per particle, without end, and freeze the client within seconds. Now the spawn is refused (one
 * log line), the editor refuses the pick with an explanation, and ordinary spawns still run.
 * <p>
 * Runs real emitters against the client level, like the Photon 1 compatibility checks around it; the probe
 * effects live in {@link FXHelper}'s cache only while it runs.
 */
@OnlyIn(Dist.CLIENT)
public final class SubEmitterCycleRegression {
    private SubEmitterCycleRegression() {
    }

    public static void verify(Level level) {
        verifyChainRules();
        verifySpawns(level);
    }

    private static void verifyChainRules() {
        var a = "photon_probe:cycle_a";
        var b = "photon_probe:cycle_b";
        var c = "photon_probe:cycle_c";
        check(SpawnChain.refusal(List.of(a), List.of(), a) != null, "an effect spawning itself was not refused");
        check(SpawnChain.refusal(List.of(a), List.of(), b) == null, "spawning another effect was refused");
        check(SpawnChain.refusal(List.of(a), List.of(b), a) != null, "a -> b -> a was not refused");
        check(SpawnChain.refusal(List.of(), List.of(b), b) != null, "b -> b under an unknown root was not refused");
        check(SpawnChain.refusal(List.of(), List.of(), a) == null, "a first spawn under an unknown root was refused");
        var deep = new ArrayList<String>();
        for (int i = 0; i < SpawnChain.MAX_DEPTH; i++) deep.add("photon_probe:depth_" + i);
        check(SpawnChain.refusal(List.of(a), deep, c) != null, "a chain deeper than MAX_DEPTH was not refused");
        check(SpawnChain.refusal(List.of(a), deep.subList(0, SpawnChain.MAX_DEPTH - 1), c) == null,
                "a chain within MAX_DEPTH was refused");
        check(SpawnChain.extend(List.of(a), b).equals(List.of(a, b)), "extend did not append the spawned id");
        check(SpawnChain.describe(List.of(a), List.of(b), c).equals(a + " -> " + b + " -> " + c),
                "describe did not spell the chain: " + SpawnChain.describe(List.of(a), List.of(b), c));
        check(SpawnChain.legacyId("spark").equals("#spark"), "legacy sub-effect ids must not look like locations");
    }

    private static void verifySpawns(Level level) {
        var selfId = new ResourceLocation("photon_probe", "sub_emitter_cycle/self");
        var leafId = new ResourceLocation("photon_probe", "sub_emitter_cycle/leaf");
        var pingId = new ResourceLocation("photon_probe", "sub_emitter_cycle/ping");
        var pongId = new ResourceLocation("photon_probe", "sub_emitter_cycle/pong");
        var self = new CountingFX(selfId, selfId);
        var leaf = new CountingFX(leafId, null);
        var ping = new CountingFX(pingId, pongId);
        var pong = new CountingFX(pongId, pingId);
        var cache = fxCache();
        var runtimes = new ArrayList<FXRuntime>();
        cache.put(selfId, self);
        cache.put(leafId, leaf);
        cache.put(pingId, ping);
        cache.put(pongId, pong);
        try {
            // 1. an effect that knows its id refuses to spawn itself at the first level
            var selfRuntime = self.createRuntime();
            runtimes.add(selfRuntime);
            selfRuntime.emit(new ProbeExecutor(level, List.of(SpawnChain.id(selfId))));
            var selfEmitter = emitterOf(selfRuntime);
            int selfBefore = self.created.size();
            tick(selfEmitter, 3);
            check(selfEmitter.getParticleAmount() > 0, "the probe emitter emitted no particles");
            check(self.created.size() == selfBefore, "an effect spawning itself was spawned "
                    + (self.created.size() - selfBefore) + " time(s)");

            // 2. under an executor that does not know the id, the first level runs but the copies spawn no more
            var blindRuntime = self.createRuntime();
            runtimes.add(blindRuntime);
            blindRuntime.emit((IEffectExecutor) () -> level);
            int blindBefore = self.created.size();
            tick(emitterOf(blindRuntime), 3);
            check(self.created.size() > blindBefore, "a first-level spawn under an unknown root did not run");
            var copy = self.created.get(blindBefore);
            runtimes.addAll(self.created.subList(blindBefore, self.created.size()));
            check(copy.spawnChain().equals(List.of(SpawnChain.id(selfId))), "the spawned copy did not record its chain: " + copy.spawnChain());
            int copyBefore = self.created.size();
            var copyEmitter = emitterOf(copy);
            tick(copyEmitter, 3);
            check(copyEmitter.getParticleAmount() > 0, "the spawned copy emitted no particles");
            check(self.created.size() == copyBefore, "the spawned copy spawned the effect again "
                    + (self.created.size() - copyBefore) + " time(s)");

            // 3. a legitimate spawn still runs, and the mutual cycle stops at the second level
            var pingRuntime = ping.createRuntime();
            runtimes.add(pingRuntime);
            pingRuntime.emit(new ProbeExecutor(level, List.of(SpawnChain.id(pingId))));
            int pongBefore = pong.created.size();
            tick(emitterOf(pingRuntime), 3);
            check(pong.created.size() > pongBefore, "ping did not spawn pong");
            runtimes.addAll(pong.created.subList(pongBefore, pong.created.size()));
            var pongRuntime = pong.created.get(pongBefore);
            check(pongRuntime.spawnChain().equals(List.of(SpawnChain.id(pongId))), "pong did not record its chain: " + pongRuntime.spawnChain());
            int pingBefore = ping.created.size();
            var pongEmitter = emitterOf(pongRuntime);
            tick(pongEmitter, 3);
            check(pongEmitter.getParticleAmount() > 0, "pong emitted no particles");
            check(ping.created.size() == pingBefore, "pong spawned ping back " + (ping.created.size() - pingBefore) + " time(s)");

            // 4. what the editor's picker asks before accepting an id
            check(selfId.equals(SubEmittersSetting.Emitter.cycleThrough(Set.of(selfId), selfId, 64)),
                    "picking the edited effect itself was not refused");
            check(pingId.equals(SubEmittersSetting.Emitter.cycleThrough(Set.of(pingId), pongId, 64)),
                    "picking pong from ping was not refused through ping");
            check(SubEmittersSetting.Emitter.cycleThrough(Set.of(pingId), leafId, 64) == null,
                    "picking an effect that spawns nothing was refused");
            check(SubEmittersSetting.Emitter.cycleThrough(Set.of(), pongId, 64) == null,
                    "an unsaved project without ids refused a pick");
            check(SubEmittersSetting.Emitter.spawnsOf(ping).equals(Set.of(pongId)), "spawnsOf missed the sub-emitter: "
                    + SubEmittersSetting.Emitter.spawnsOf(ping));
        } finally {
            for (var runtime : runtimes) {
                try {
                    runtime.destroy(true);
                } catch (RuntimeException ignored) {
                    // a probe runtime that never fully emitted has nothing to tear down
                }
            }
            cache.remove(selfId, self);
            cache.remove(leafId, leaf);
            cache.remove(pingId, ping);
            cache.remove(pongId, pong);
        }
    }

    private static void tick(ParticleEmitter emitter, int ticks) {
        for (int i = 0; i < ticks; i++) emitter.update(1f);
    }

    private static ParticleEmitter emitterOf(FXRuntime runtime) {
        for (var object : runtime.objects.values()) {
            if (object instanceof ParticleEmitter emitter) return emitter;
        }
        throw new IllegalStateException("probe runtime holds no particle emitter");
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, FX> fxCache() {
        try {
            var field = FXHelper.class.getDeclaredField("CACHE");
            field.setAccessible(true);
            return (Map<ResourceLocation, FX>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FXHelper.CACHE is not reachable for the sub-emitter cycle probe", e);
        }
    }

    private static void check(boolean condition, String problem) {
        if (!condition) throw new IllegalStateException("[sub-emitter-cycle] " + problem);
    }

    /** An effect of one emitter (three particles a tick) whose sub-emitter spawns {@code spawns} at every birth. */
    private static final class CountingFX extends FX {
        final List<FXRuntime> created = new ArrayList<>();

        CountingFX(ResourceLocation id, @Nullable ResourceLocation spawns) {
            setFxLocation(id);
            var emitter = new ParticleEmitter();
            emitter.config.emission.setEmissionRate(NumberFunction.constant(3));
            if (spawns != null) {
                var sub = new SubEmittersSetting.Emitter();
                sub.setFxLocation(spawns);
                sub.setEvent(SubEmittersSetting.Event.Birth);
                sub.setEmitProbability(NumberFunction.constant(1));
                emitter.config.subEmitters.getEmitters().add(sub);
                emitter.config.subEmitters.setEnable(true);
            }
            getFxData().objects().add(emitter);
        }

        @Override
        public FXRuntime createRuntime() {
            var runtime = super.createRuntime();
            created.add(runtime);
            return runtime;
        }
    }

    private record ProbeExecutor(Level level, List<String> ids) implements IEffectExecutor {
        @Override
        public Level getLevel() {
            return level;
        }

        @Override
        public List<String> playingAs() {
            return ids;
        }
    }
}
