package com.lowdragmc.photon.client.fx;

import com.lowdragmc.lowdraglib2.client.UIRenderStateScope;
import com.lowdragmc.lowdraglib2.client.scene.FBOWorldSceneRenderer;
import com.lowdragmc.lowdraglib2.client.scene.ParticleManager;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld;
import com.lowdragmc.photon.client.FXSceneOptions;
import com.lowdragmc.photon.client.PhotonParticleManager;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.postfx.runtime.PostEffectStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.function.Predicate;

/** Simulates and renders an effect once in an isolated FBO to warm its resources and shaders. */
@OnlyIn(Dist.CLIENT)
public final class FXWarmup {
    private static boolean running;

    private FXWarmup() {}

    public static Result warmup(ResourceLocation id) { return warmup(id, 1); }

    public static Result warmup(ResourceLocation id, int ticks) {
        checkReady(ticks);
        FX fx = FXHelper.getFX(Objects.requireNonNull(id, "id"));
        if (fx == null) throw new IllegalArgumentException("FX not found: " + id);
        return warmup(fx, ticks);
    }

    public static Result warmup(FX fx) { return warmup(fx, 1); }

    public static Result warmup(FX fx, int ticks) {
        checkReady(ticks);
        Objects.requireNonNull(fx, "fx");
        long start = System.nanoTime();
        float previousShaderTime = RenderSystem.getShaderGameTime() * 24000.0f;
        running = true;
        try (UIRenderStateScope ignored = UIRenderStateScope.capture()) {
            Minecraft minecraft = Minecraft.getInstance();
            Level activeLevel = minecraft.level;
            DummyWorld level = activeLevel == null ? new DummyWorld() : new DummyWorld(activeLevel.registryAccess());
            PhotonParticleManager manager = new PhotonParticleManager(FXSceneOptions.DEFAULT) {
                @Override
                public void render(PoseStack pose, Camera camera, float partial, Predicate<ParticleRenderType> filter) {
                    super.render(pose, camera, 1.0f, filter);
                }
            };
            FBOWorldSceneRenderer renderer = new FBOWorldSceneRenderer(level, 64, 64);
            PostEffectStack sink = new PostEffectStack();
            FXRuntime runtime = null;
            int particleCount = 0;
            try {
                renderer.setParticleManager((ParticleManager) manager);
                renderer.setCameraLookAt(new Vector3f(), 8.0, Math.toRadians(45.0), Math.toRadians(15.0));
                runtime = fx.createRuntime();
                runtime.timelinePlayer.setSignalDispatch(false);
                runtime.timelinePlayer.setAudioDispatch(false);
                runtime.emit(new IEffectExecutor() {
                    @Override public Level getLevel() { return level; }
                    @Override public boolean allowTimelineEvents() { return false; }
                    @Override public PostEffectStack postEffectSink() { return sink; }
                });
                manager.tickInternal();
                manager.setTime(0L);
                for (int i = 0; i < ticks; i++) manager.tickInternal();
                manager.play();
                renderer.drawScene(0, 0, 64, 64, -1, -1);
                particleCount = runtime.objects.values().stream()
                        .filter(ParticleEmitter.class::isInstance)
                        .map(ParticleEmitter.class::cast)
                        .mapToInt(ParticleEmitter::getParticleAmount)
                        .sum();
            } finally {
                try {
                    if (runtime != null) runtime.destroy(true);
                } finally {
                    manager.clear();
                    sink.onFrameEnd();
                    renderer.releaseResource();
                }
            }
            return new Result(ticks, particleCount, System.nanoTime() - start);
        } finally {
            RenderSystem.setShaderGameTime((long) previousShaderTime,
                    previousShaderTime - (float) ((long) previousShaderTime));
            running = false;
        }
    }

    private static void checkReady(int ticks) {
        RenderSystem.assertOnRenderThread();
        if (ticks < 1 || ticks > 10_000) throw new IllegalArgumentException("ticks must be in [1, 10000]");
        if (running || PhotonParticleManager.getRenderingManager() != null) {
            throw new IllegalStateException("Warmup must not run inside another FX/scene render");
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() instanceof LoadingOverlay || minecraft.gameRenderer == null) {
            throw new IllegalStateException("Warmup requires completed client resource loading");
        }
    }

    public record Result(int ticks, int particles, long elapsedNanos) {}
}


