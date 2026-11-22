package com.lowdragmc.photon.client.fx;

import com.lowdragmc.photon.client.gameobject.emitter.data.SubEmittersSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Smoke regression for {@link FXHelper}'s memory of effects that could not be loaded. An id that no pack provides
 * used to be looked up again on every ask: a sub-emitter pointing at an effect that was not on the instance walked
 * every pack and logged a stack trace per particle it emitted (a report counted 63 traces in six minutes, one every
 * few frames). Now the miss is read and logged once, answered from memory for {@link FXHelper#MISSING_RETRY_NANOS},
 * retried silently after that, and forgotten by {@link FXHelper#invalidate} and {@link FXHelper#clearCache} so a
 * file that appears meanwhile is picked up.
 * <p>
 * The last part runs a real emitter against the client level, like {@code SubEmitterCycleRegression}: a sub-emitter
 * that spawns the missing effect at every birth must cost one read however many particles are born.
 */
@OnlyIn(Dist.CLIENT)
public final class MissingEffectRegression {
    private MissingEffectRegression() {
    }

    public static void verify(Level level) {
        var missing = new ResourceLocation("photon_probe", "missing_effect/" + Long.toUnsignedString(System.nanoTime(), 36));
        int reads = FXHelper.readAttempts(missing);
        check(FXHelper.getFX(missing) == null, "an effect no pack provides was loaded");
        check(FXHelper.readAttempts(missing) == reads + 1, "the first ask for a missing effect read " + (FXHelper.readAttempts(missing) - reads) + " file(s)");
        check(FXHelper.isKnownMissing(missing), "the missing effect was not remembered");
        for (int i = 0; i < 200; i++) {
            check(FXHelper.getFX(missing) == null, "a remembered missing effect was answered with an effect");
        }
        check(FXHelper.readAttempts(missing) == reads + 1, "200 asks for a remembered missing effect read the packs "
                + (FXHelper.readAttempts(missing) - reads - 1) + " more time(s)");

        // once the cool-down is over the next ask reads again (silently) and the miss is remembered again
        FXHelper.missingRetryNanos = 0;
        try {
            check(FXHelper.getFX(missing) == null, "the retry after the cool-down was answered with an effect");
            check(FXHelper.readAttempts(missing) == reads + 2, "the retry after the cool-down did not read the packs");
        } finally {
            FXHelper.missingRetryNanos = FXHelper.MISSING_RETRY_NANOS;
        }
        // (remembered with the restored cool-down: the retry above ran with a zero cool-down, so ask once more)
        check(FXHelper.getFX(missing) == null, "the ask after the retry was answered with an effect");
        check(FXHelper.readAttempts(missing) == reads + 3, "the ask after a zero cool-down retry did not read the packs");
        check(FXHelper.isKnownMissing(missing), "the retried missing effect was not remembered");
        check(FXHelper.getFX(missing) == null && FXHelper.readAttempts(missing) == reads + 3,
                "a remembered miss was read again within the cool-down");

        // a reload or an invalidation forgets the miss, so an effect exported meanwhile is found at once
        FXHelper.invalidate(List.of(missing));
        check(!FXHelper.isKnownMissing(missing), "invalidate kept the missing effect in memory");

        // the report's path: a sub-emitter naming the missing effect, spawning at every birth
        var probe = new FX();
        probe.setFxLocation(new ResourceLocation("photon_probe", "missing_effect/parent"));
        var emitter = new ParticleEmitter();
        emitter.config.emission.setEmissionRate(NumberFunction.constant(3));
        var sub = new SubEmittersSetting.Emitter();
        sub.setFxLocation(missing);
        sub.setEvent(SubEmittersSetting.Event.Birth);
        sub.setEmitProbability(NumberFunction.constant(1));
        emitter.config.subEmitters.getEmitters().add(sub);
        emitter.config.subEmitters.setEnable(true);
        probe.getFxData().objects().add(emitter);
        var runtime = probe.createRuntime();
        try {
            runtime.emit((IEffectExecutor) () -> level);
            ParticleEmitter running = null;
            for (var object : runtime.objects.values()) {
                if (object instanceof ParticleEmitter candidate) running = candidate;
            }
            check(running != null, "the probe runtime holds no particle emitter");
            int before = FXHelper.readAttempts(missing);
            for (int tick = 0; tick < 10; tick++) running.update(1f);
            check(running.getParticleAmount() > 0, "the probe emitter emitted no particles");
            check(FXHelper.readAttempts(missing) == before + 1, "a sub-emitter naming a missing effect read the packs "
                    + (FXHelper.readAttempts(missing) - before) + " time(s) over ten ticks (expected once)");
        } finally {
            try {
                runtime.destroy(true);
            } catch (RuntimeException ignored) {
                // a probe runtime that never fully emitted has nothing to tear down
            }
            FXHelper.invalidate(List.of(missing));
        }
    }

    private static void check(boolean condition, String problem) {
        if (!condition) throw new IllegalStateException("[missing-effect] " + problem);
    }
}
